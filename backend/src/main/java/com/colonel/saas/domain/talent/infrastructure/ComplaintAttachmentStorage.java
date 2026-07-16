package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.talent.policy.ComplaintImagePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** 不对外静态暴露的达人投诉附件文件存储。 */
@Slf4j
@Component
public class ComplaintAttachmentStorage {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_DELETE_ATTEMPTS = 3;
    private static final int MAX_STORAGE_KEY_ATTEMPTS = 3;
    private static final int MAX_RECONCILE_BATCH = 100;
    private static final int MAX_CURSOR_BYTES = 128;
    private static final String CURSOR_FILENAME = ".reconcile-cursor";
    private static final Pattern GENERATED_KEY_PATTERN =
            Pattern.compile("[0-9a-f]{2}/[0-9a-f]{32}\\.(jpg|png|webp)");
    private static final Pattern TEMPORARY_KEY_PATTERN =
            Pattern.compile("[0-9a-f]{2}/\\.[0-9a-f]{32}-[^/\\\\]+\\.tmp");
    private static final Pattern CURSOR_TEMPORARY_NAME_PATTERN =
            Pattern.compile("\\.reconcile-cursor-[^/\\\\]+\\.tmp");
    private static final Comparator<ReconcileCandidate> CANDIDATE_ORDER =
            Comparator.comparing(ReconcileCandidate::storageKey);

    private final Path storageRoot;
    private final Supplier<UUID> uuidSupplier;
    private final MoveOperation moveOperation;
    private final LinkOperation linkOperation;
    private final DeleteOperation deleteOperation;
    private final CandidateAttributesReader candidateAttributesReader;

    @Autowired
    public ComplaintAttachmentStorage(
            @Value("${talent.complaint.storage-root:/var/lib/colonel-saas/complaints}")
            String storageRoot) {
        this(Path.of(storageRoot));
    }

    public ComplaintAttachmentStorage(Path storageRoot) {
        this(
                storageRoot,
                UUID::randomUUID,
                ComplaintAttachmentStorage::moveFile,
                Files::createLink,
                Files::deleteIfExists,
                ComplaintAttachmentStorage::readCandidateAttributes);
    }

    ComplaintAttachmentStorage(
            Path storageRoot,
            Supplier<UUID> uuidSupplier,
            MoveOperation moveOperation) {
        this(
                storageRoot,
                uuidSupplier,
                moveOperation,
                Files::createLink,
                Files::deleteIfExists,
                ComplaintAttachmentStorage::readCandidateAttributes);
    }

    ComplaintAttachmentStorage(
            Path storageRoot,
            Supplier<UUID> uuidSupplier,
            MoveOperation moveOperation,
            DeleteOperation deleteOperation) {
        this(
                storageRoot,
                uuidSupplier,
                moveOperation,
                Files::createLink,
                deleteOperation,
                ComplaintAttachmentStorage::readCandidateAttributes);
    }

    ComplaintAttachmentStorage(
            Path storageRoot,
            Supplier<UUID> uuidSupplier,
            MoveOperation moveOperation,
            DeleteOperation deleteOperation,
            CandidateAttributesReader candidateAttributesReader) {
        this(
                storageRoot,
                uuidSupplier,
                moveOperation,
                Files::createLink,
                deleteOperation,
                candidateAttributesReader);
    }

    ComplaintAttachmentStorage(
            Path storageRoot,
            Supplier<UUID> uuidSupplier,
            MoveOperation moveOperation,
            LinkOperation linkOperation,
            DeleteOperation deleteOperation,
            CandidateAttributesReader candidateAttributesReader) {
        if (storageRoot == null) {
            throw new IllegalArgumentException("storageRoot is required");
        }
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
        this.moveOperation = Objects.requireNonNull(moveOperation, "moveOperation");
        this.linkOperation = Objects.requireNonNull(linkOperation, "linkOperation");
        this.deleteOperation = Objects.requireNonNull(deleteOperation, "deleteOperation");
        this.candidateAttributesReader = Objects.requireNonNull(
                candidateAttributesReader, "candidateAttributesReader");
    }

    public StoredAttachment store(ComplaintImagePolicy.ValidatedImage image) {
        if (image == null) {
            throw BusinessException.param("投诉图片不能为空");
        }
        String random = nextRandomHex();
        Path temporary = null;
        try {
            Path firstTarget = resolveForWrite(storageKey(random, image.extension()));
            temporary = Files.createTempFile(
                    firstTarget.getParent(), "." + random + "-", ".tmp");
            StreamDigest stored = writeAndDigest(image.source(), temporary);
            if (stored.size() != image.actualSize()
                    || !MessageDigest.isEqual(stored.sha256(), image.sha256())) {
                throw BusinessException.param("投诉附件内容在校验后发生变化");
            }
            FileAlreadyExistsException collision = null;
            for (int attempt = 1; attempt <= MAX_STORAGE_KEY_ATTEMPTS; attempt++) {
                if (attempt > 1) {
                    random = nextRandomHex();
                }
                String storageKey = storageKey(random, image.extension());
                Path target = resolveForWrite(storageKey);
                try {
                    commitWithoutOverwrite(temporary, target);
                    temporary = null;
                    return new StoredAttachment(
                            storageKey,
                            image.originalName(),
                            image.contentType(),
                            stored.size(),
                            HexFormat.of().formatHex(stored.sha256()));
                } catch (FileAlreadyExistsException occupied) {
                    collision = occupied;
                }
            }
            throw collision == null
                    ? new IOException("Complaint attachment key allocation failed")
                    : collision;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("投诉附件写入失败", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private String nextRandomHex() {
        return Objects.requireNonNull(uuidSupplier.get(), "generated UUID")
                .toString().replace("-", "");
    }

    private String storageKey(String random, String extension) {
        return random.substring(0, 2) + "/" + random + "." + extension;
    }

    public byte[] load(String storageKey) {
        Path candidate = resolveExisting(storageKey);
        try {
            return Files.readAllBytes(candidate);
        } catch (IOException exception) {
            throw BusinessException.notFound("投诉附件不存在");
        }
    }

    public void deleteQuietly(String storageKey) {
        if (!isGeneratedStorageKey(storageKey)) {
            return;
        }
        deleteWithRetry(storageKey, null, () -> resolveForDelete(storageKey));
    }

    /**
     * 扫描超过 grace 的规范随机键候选；目录可以全扫，但两个有界堆各自最多保留
     * {@code batch} 项，因此常驻内存为 O(batch)，不随附件总量增长。
     */
    public List<ReconcileCandidate> findReconcileCandidates(
            Instant cutoff,
            int requestedLimit) {
        Objects.requireNonNull(cutoff, "cutoff");
        int limit = Math.max(1, Math.min(requestedLimit, MAX_RECONCILE_BATCH));
        Path root;
        try {
            root = safeStorageRoot();
        } catch (IOException | RuntimeException exception) {
            logScanWarning("root", exception);
            return List.of();
        }

        String cursor = readReconcileCursor(root);
        PriorityQueue<ReconcileCandidate> afterCursor =
                new PriorityQueue<>(limit, CANDIDATE_ORDER.reversed());
        PriorityQueue<ReconcileCandidate> wrapped =
                new PriorityQueue<>(limit, CANDIDATE_ORDER.reversed());
        try (DirectoryStream<Path> prefixes = Files.newDirectoryStream(root)) {
            for (Path prefix : prefixes) {
                scanAttachmentPrefix(
                        prefix, cutoff, cursor, limit, afterCursor, wrapped);
            }
        } catch (IOException | RuntimeException exception) {
            logScanWarning("root-entries", exception);
        }
        List<ReconcileCandidate> selected = sorted(afterCursor);
        if (selected.size() < limit) {
            List<ReconcileCandidate> wrappedCandidates = sorted(wrapped);
            selected.addAll(wrappedCandidates.subList(
                    0, Math.min(limit - selected.size(), wrappedCandidates.size())));
        }
        return List.copyOf(selected);
    }

    /** 扫描超过 grace 的受控上传临时文件；不查数据库且不跟随符号链接。 */
    public List<TemporaryCandidate> findTemporaryCandidates(
            Instant cutoff,
            int requestedLimit) {
        Objects.requireNonNull(cutoff, "cutoff");
        int limit = Math.max(1, Math.min(requestedLimit, MAX_RECONCILE_BATCH));
        List<TemporaryCandidate> candidates = new ArrayList<>(limit);
        Path root;
        try {
            root = safeStorageRoot();
        } catch (IOException | RuntimeException exception) {
            logScanWarning("temporary-root", exception);
            return List.of();
        }
        try (DirectoryStream<Path> prefixes = Files.newDirectoryStream(root)) {
            for (Path prefix : prefixes) {
                if (candidates.size() >= limit) {
                    break;
                }
                scanTemporaryPrefix(prefix, cutoff, limit, candidates);
            }
        } catch (IOException | RuntimeException exception) {
            logScanWarning("temporary-root-entries", exception);
        }
        return List.copyOf(candidates);
    }

    /** 删除扫描时 mtime 未变化的孤儿候选；失败最多重试三次。 */
    public DeleteResult deleteReconcileCandidate(ReconcileCandidate candidate) {
        if (candidate == null || candidate.lastModifiedTime() == null) {
            return DeleteResult.SKIPPED;
        }
        if (!isGeneratedStorageKey(candidate.storageKey())) {
            return DeleteResult.SKIPPED;
        }
        return deleteWithRetry(
                candidate.storageKey(),
                candidate.lastModifiedTime(),
                () -> resolveForDelete(candidate.storageKey()));
    }

    public DeleteResult deleteTemporaryCandidate(TemporaryCandidate candidate) {
        if (candidate == null
                || candidate.lastModifiedTime() == null
                || !isTemporaryStorageKey(candidate.storageKey())) {
            return DeleteResult.SKIPPED;
        }
        return deleteWithRetry(
                candidate.storageKey(),
                candidate.lastModifiedTime(),
                () -> resolveTemporaryForDelete(candidate.storageKey()));
    }

    private void scanAttachmentPrefix(
            Path prefix,
            Instant cutoff,
            String cursor,
            int limit,
            PriorityQueue<ReconcileCandidate> afterCursor,
            PriorityQueue<ReconcileCandidate> wrapped) {
        String prefixName = prefix.getFileName() == null ? "" : prefix.getFileName().toString();
        if (!isSafePrefix(prefixName, prefix)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(prefix)) {
            for (Path file : files) {
                ReconcileCandidate candidate = readAttachmentCandidate(
                        prefixName, file, cutoff);
                if (candidate == null) {
                    continue;
                }
                PriorityQueue<ReconcileCandidate> destination = cursor == null
                                || candidate.storageKey().compareTo(cursor) > 0
                        ? afterCursor
                        : wrapped;
                offerSmallest(destination, candidate, limit, CANDIDATE_ORDER);
            }
        } catch (IOException | RuntimeException exception) {
            logScanWarning(prefixName, exception);
        }
    }

    private ReconcileCandidate readAttachmentCandidate(
            String prefixName,
            Path file,
            Instant cutoff) {
        String filename = file.getFileName() == null ? "" : file.getFileName().toString();
        String storageKey = prefixName + "/" + filename;
        if (!GENERATED_KEY_PATTERN.matcher(storageKey).matches()
                || Files.isSymbolicLink(file)) {
            return null;
        }
        try {
            BasicFileAttributes attributes = candidateAttributesReader.read(file);
            if (!attributes.isRegularFile()
                    || attributes.lastModifiedTime().toInstant().isAfter(cutoff)) {
                return null;
            }
            return new ReconcileCandidate(storageKey, attributes.lastModifiedTime());
        } catch (IOException | RuntimeException exception) {
            logScanWarning(storageKey, exception);
            return null;
        }
    }

    private void scanTemporaryPrefix(
            Path prefix,
            Instant cutoff,
            int limit,
            List<TemporaryCandidate> candidates) {
        String prefixName = prefix.getFileName() == null ? "" : prefix.getFileName().toString();
        if (!isSafePrefix(prefixName, prefix)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(prefix)) {
            for (Path file : files) {
                if (candidates.size() >= limit) {
                    return;
                }
                String filename = file.getFileName() == null ? "" : file.getFileName().toString();
                String storageKey = prefixName + "/" + filename;
                if (!isTemporaryStorageKey(storageKey) || Files.isSymbolicLink(file)) {
                    continue;
                }
                try {
                    BasicFileAttributes attributes = candidateAttributesReader.read(file);
                    if (attributes.isRegularFile()
                            && !attributes.lastModifiedTime().toInstant().isAfter(cutoff)) {
                        candidates.add(new TemporaryCandidate(
                                storageKey, attributes.lastModifiedTime()));
                    }
                } catch (IOException | RuntimeException exception) {
                    logScanWarning(storageKey, exception);
                }
            }
        } catch (IOException | RuntimeException exception) {
            logScanWarning(prefixName, exception);
        }
    }

    private boolean isSafePrefix(String prefixName, Path prefix) {
        return prefixName.matches("[0-9a-f]{2}")
                && !Files.isSymbolicLink(prefix)
                && Files.isDirectory(prefix, LinkOption.NOFOLLOW_LINKS);
    }

    private <T> void offerSmallest(
            PriorityQueue<T> heap,
            T candidate,
            int limit,
            Comparator<T> ascending) {
        if (heap.size() < limit) {
            heap.offer(candidate);
            return;
        }
        T largest = heap.peek();
        if (largest != null && ascending.compare(candidate, largest) < 0) {
            heap.poll();
            heap.offer(candidate);
        }
    }

    private List<ReconcileCandidate> sorted(PriorityQueue<ReconcileCandidate> heap) {
        List<ReconcileCandidate> values = new ArrayList<>(heap);
        values.sort(CANDIDATE_ORDER);
        return values;
    }

    private DeleteResult deleteWithRetry(
            String safeIdentifier,
            FileTime expectedLastModified,
            DeleteTargetResolver targetResolver) {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_DELETE_ATTEMPTS; attempt++) {
            Path candidate;
            try {
                candidate = targetResolver.resolve();
                if (expectedLastModified != null) {
                    FileTime current = Files.getLastModifiedTime(
                            candidate, LinkOption.NOFOLLOW_LINKS);
                    if (!current.equals(expectedLastModified)) {
                        return DeleteResult.SKIPPED;
                    }
                }
                return deleteOperation.deleteIfExists(candidate)
                        ? DeleteResult.DELETED
                        : DeleteResult.SKIPPED;
            } catch (NoSuchFileException missing) {
                return DeleteResult.SKIPPED;
            } catch (BusinessException unsafe) {
                return DeleteResult.SKIPPED;
            } catch (IOException exception) {
                lastFailure = exception;
            }
        }
        log.warn(
                "complaint_attachment_delete_failed keyFingerprint={} attempts={} errorType={}",
                fingerprint(safeIdentifier),
                MAX_DELETE_ATTEMPTS,
                lastFailure == null ? "unknown" : lastFailure.getClass().getSimpleName());
        return DeleteResult.FAILED;
    }

    private boolean isGeneratedStorageKey(String storageKey) {
        return storageKey != null && GENERATED_KEY_PATTERN.matcher(storageKey).matches();
    }

    private boolean isTemporaryStorageKey(String storageKey) {
        return storageKey != null && TEMPORARY_KEY_PATTERN.matcher(storageKey).matches();
    }

    private void logScanWarning(String entry, Exception exception) {
        log.warn(
                "complaint_attachment_scan_entry_failed entryFingerprint={} errorType={}",
                fingerprint(entry),
                exception.getClass().getSimpleName());
    }

    private String fingerprint(String value) {
        byte[] bytes = value == null
                ? new byte[0]
                : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(sha256Digest().digest(bytes)).substring(0, 12);
    }

    private StreamDigest writeAndDigest(
            org.springframework.web.multipart.MultipartFile source,
            Path temporary) throws IOException {
        MessageDigest digest = sha256Digest();
        long size = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = source.getInputStream();
             OutputStream output = Files.newOutputStream(
                     temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                size += read;
                if (size > ComplaintImagePolicy.MAX_FILE_SIZE) {
                    throw BusinessException.param("单张投诉图片不能超过 10MB");
                }
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
        }
        return new StreamDigest(size, digest.digest());
    }

    private void commitWithoutOverwrite(Path temporary, Path target) throws IOException {
        try {
            linkOperation.createLink(target, temporary);
            deleteTemporary(temporary);
            return;
        } catch (FileAlreadyExistsException occupied) {
            throw occupied;
        } catch (UnsupportedOperationException | IOException linkUnavailable) {
            // Files.move without ATOMIC_MOVE/REPLACE_EXISTING preserves CREATE_NEW semantics.
        }
        try {
            moveOperation.move(temporary, target, false);
        } catch (FileAlreadyExistsException occupied) {
            throw occupied;
        }
    }

    private static void moveFile(Path source, Path target, boolean ignored) throws IOException {
        Files.move(source, target);
    }

    private static BasicFileAttributes readCandidateAttributes(Path path) throws IOException {
        return Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        String identifier = temporary.getFileName() == null
                ? "upload-temporary"
                : temporary.getFileName().toString();
        deleteWithRetry(
                identifier,
                null,
                () -> resolveUploadTemporaryForDelete(temporary));
    }

    /** 批次处理结束后推进持久游标；失败只输出脱敏告警，不重放已处理坏文件。 */
    public void advanceReconcileCursor(String storageKey) {
        if (!isGeneratedStorageKey(storageKey)) {
            throw new IllegalArgumentException("invalid complaint attachment reconcile cursor");
        }
        Path cursorTemporary = null;
        try {
            Path root = safeStorageRoot();
            Path cursor = root.resolve(CURSOR_FILENAME);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attributes = Files.readAttributes(
                        cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile() || Files.isSymbolicLink(cursor)) {
                    logCursorWarning("unsafe-existing", null);
                    return;
                }
            }
            cursorTemporary = Files.createTempFile(root, ".reconcile-cursor-", ".tmp");
            Files.writeString(
                    cursorTemporary,
                    storageKey,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            if (Files.isSymbolicLink(cursor)) {
                logCursorWarning("unsafe-race", null);
                return;
            }
            try {
                Files.move(
                        cursorTemporary,
                        cursor,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(cursorTemporary, cursor, StandardCopyOption.REPLACE_EXISTING);
            }
            cursorTemporary = null;
        } catch (IOException | RuntimeException exception) {
            logCursorWarning("write-failed", exception);
        } finally {
            deleteInternalTemporary(cursorTemporary);
        }
    }

    private String readReconcileCursor(Path root) {
        Path cursor = root.resolve(CURSOR_FILENAME);
        if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()
                    || Files.isSymbolicLink(cursor)
                    || attributes.size() > MAX_CURSOR_BYTES) {
                logCursorWarning("invalid-attributes", null);
                return null;
            }
            ByteBuffer buffer = ByteBuffer.allocate(MAX_CURSOR_BYTES + 1);
            try (SeekableByteChannel channel = Files.newByteChannel(
                    cursor,
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                    // bounded read
                }
            }
            if (buffer.position() > MAX_CURSOR_BYTES) {
                logCursorWarning("too-large", null);
                return null;
            }
            buffer.flip();
            String value = StandardCharsets.UTF_8.decode(buffer).toString();
            if (!isGeneratedStorageKey(value)) {
                logCursorWarning("invalid-format", null);
                return null;
            }
            return value;
        } catch (IOException | RuntimeException exception) {
            logCursorWarning("read-failed", exception);
            return null;
        }
    }

    private void logCursorWarning(String reason, Exception exception) {
        log.warn(
                "complaint_attachment_cursor_invalid reason={} errorType={}",
                reason,
                exception == null ? "none" : exception.getClass().getSimpleName());
    }

    private void deleteInternalTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        deleteWithRetry(
                "cursor-temporary",
                null,
                () -> resolveInternalTemporaryForDelete(temporary));
    }

    private Path resolveForWrite(String storageKey) throws IOException {
        Path normalized = resolveNormalized(storageKey);
        Path realRoot = safeStorageRoot();
        Path realParent = Files.createDirectories(normalized.getParent()).toRealPath();
        if (!realParent.startsWith(realRoot)) {
            throw new IOException("Complaint attachment path escaped storage root");
        }
        return realParent.resolve(normalized.getFileName());
    }

    private Path resolveExisting(String storageKey) {
        Path normalized = resolveNormalized(storageKey);
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        try {
            Path realRoot = Files.createDirectories(storageRoot).toRealPath();
            Path realFile = normalized.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                throw BusinessException.notFound("投诉附件不存在");
            }
            return realFile;
        } catch (IOException exception) {
            throw BusinessException.notFound("投诉附件不存在");
        }
    }

    private Path resolveForDelete(String storageKey) throws IOException {
        if (!isGeneratedStorageKey(storageKey)) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        return resolveSafeRegularFile(storageKey);
    }

    private Path resolveTemporaryForDelete(String storageKey) throws IOException {
        if (!isTemporaryStorageKey(storageKey)) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        return resolveSafeRegularFile(storageKey);
    }

    private Path resolveUploadTemporaryForDelete(Path temporary) throws IOException {
        Path normalized = temporary.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        Path root = safeStorageRoot();
        Path realParent = normalized.getParent().toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path realTemporary = realParent.resolve(normalized.getFileName());
        if (!realParent.startsWith(root)) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        String storageKey = root.relativize(realTemporary)
                .toString().replace('\\', '/');
        if (!isTemporaryStorageKey(storageKey)) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        BasicFileAttributes attributes = Files.readAttributes(
                realTemporary, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        return realTemporary;
    }

    private Path resolveInternalTemporaryForDelete(Path temporary) throws IOException {
        Path normalized = temporary.toAbsolutePath().normalize();
        String filename = normalized.getFileName() == null
                ? ""
                : normalized.getFileName().toString();
        if (!CURSOR_TEMPORARY_NAME_PATTERN.matcher(filename).matches()
                || Files.isSymbolicLink(normalized)) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        Path root = safeStorageRoot();
        Path realParent = normalized.getParent().toRealPath(LinkOption.NOFOLLOW_LINKS);
        BasicFileAttributes attributes = Files.readAttributes(
                normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!realParent.equals(root) || !attributes.isRegularFile()) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        return normalized;
    }

    private Path resolveSafeRegularFile(String storageKey) throws IOException {
        Path normalized = resolveNormalized(storageKey);
        if (Files.isSymbolicLink(storageRoot)
                || Files.isSymbolicLink(normalized.getParent())) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        Path realRoot = safeStorageRoot();
        Path realParent = normalized.getParent().toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realParent.startsWith(realRoot)) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        BasicFileAttributes attributes = Files.readAttributes(
                normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        return normalized;
    }

    private Path safeStorageRoot() throws IOException {
        Files.createDirectories(storageRoot);
        if (Files.isSymbolicLink(storageRoot)
                || !Files.isDirectory(storageRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Complaint attachment root is unsafe");
        }
        return storageRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private Path resolveNormalized(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        Path relative;
        try {
            relative = Path.of(storageKey);
        } catch (RuntimeException invalidPath) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        if (relative.isAbsolute()) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        Path resolved = storageRoot.resolve(relative).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        return resolved;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record StoredAttachment(
            String storageKey,
            String originalName,
            String contentType,
            long fileSize,
            String sha256) {
    }

    private record StreamDigest(long size, byte[] sha256) {
    }

    public record ReconcileCandidate(String storageKey, FileTime lastModifiedTime) {
    }

    public record TemporaryCandidate(String storageKey, FileTime lastModifiedTime) {
    }

    public enum DeleteResult {
        DELETED,
        SKIPPED,
        FAILED
    }

    @FunctionalInterface
    interface MoveOperation {
        void move(Path source, Path target, boolean atomic) throws IOException;
    }

    @FunctionalInterface
    interface LinkOperation {
        void createLink(Path target, Path source) throws IOException;
    }

    @FunctionalInterface
    interface DeleteOperation {
        boolean deleteIfExists(Path path) throws IOException;
    }

    @FunctionalInterface
    interface CandidateAttributesReader {
        BasicFileAttributes read(Path path) throws IOException;
    }

    @FunctionalInterface
    private interface DeleteTargetResolver {
        Path resolve() throws IOException;
    }
}
