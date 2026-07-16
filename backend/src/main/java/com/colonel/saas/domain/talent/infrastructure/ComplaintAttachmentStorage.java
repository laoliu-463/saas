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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** 不对外静态暴露的达人投诉附件文件存储。 */
@Slf4j
@Component
public class ComplaintAttachmentStorage {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_DELETE_ATTEMPTS = 3;
    private static final int MAX_RECONCILE_BATCH = 100;
    private static final Pattern GENERATED_KEY_PATTERN =
            Pattern.compile("[0-9a-f]{2}/[0-9a-f]{32}\\.(jpg|png|webp)");

    private final Path storageRoot;
    private final Supplier<UUID> uuidSupplier;
    private final MoveOperation moveOperation;
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
                Files::deleteIfExists,
                ComplaintAttachmentStorage::readCandidateAttributes);
    }

    ComplaintAttachmentStorage(
            Path storageRoot,
            Supplier<UUID> uuidSupplier,
            MoveOperation moveOperation) {
        this(storageRoot, uuidSupplier, moveOperation, Files::deleteIfExists);
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
                deleteOperation,
                ComplaintAttachmentStorage::readCandidateAttributes);
    }

    ComplaintAttachmentStorage(
            Path storageRoot,
            Supplier<UUID> uuidSupplier,
            MoveOperation moveOperation,
            DeleteOperation deleteOperation,
            CandidateAttributesReader candidateAttributesReader) {
        if (storageRoot == null) {
            throw new IllegalArgumentException("storageRoot is required");
        }
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
        this.moveOperation = Objects.requireNonNull(moveOperation, "moveOperation");
        this.deleteOperation = Objects.requireNonNull(deleteOperation, "deleteOperation");
        this.candidateAttributesReader = Objects.requireNonNull(
                candidateAttributesReader, "candidateAttributesReader");
    }

    public StoredAttachment store(ComplaintImagePolicy.ValidatedImage image) {
        if (image == null) {
            throw BusinessException.param("投诉图片不能为空");
        }
        String random = Objects.requireNonNull(uuidSupplier.get(), "generated UUID")
                .toString().replace("-", "");
        String storageKey = random.substring(0, 2) + "/" + random + "." + image.extension();
        Path temporary = null;
        try {
            Path target = resolveForWrite(storageKey);
            temporary = Files.createTempFile(target.getParent(), "." + random + "-", ".tmp");
            StreamDigest stored = writeAndDigest(image.source(), temporary);
            if (stored.size() != image.actualSize()
                    || !MessageDigest.isEqual(stored.sha256(), image.sha256())) {
                throw BusinessException.param("投诉附件内容在校验后发生变化");
            }
            moveWithoutOverwrite(temporary, target);
            temporary = null;
            return new StoredAttachment(
                    storageKey,
                    image.originalName(),
                    image.contentType(),
                    stored.size(),
                    HexFormat.of().formatHex(stored.sha256()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("投诉附件写入失败", exception);
        } finally {
            deleteTemporary(temporary);
        }
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
        deleteWithRetry(storageKey, null);
    }

    /** 扫描超过 grace 的规范随机键候选；不递归、不跟随符号链接。 */
    public List<ReconcileCandidate> findReconcileCandidates(
            Instant cutoff,
            int requestedLimit) {
        Objects.requireNonNull(cutoff, "cutoff");
        int limit = Math.max(1, Math.min(requestedLimit, MAX_RECONCILE_BATCH));
        List<ReconcileCandidate> candidates = new ArrayList<>(limit);
        Path root;
        try {
            Files.createDirectories(storageRoot);
            if (Files.isSymbolicLink(storageRoot)
                    || !Files.isDirectory(storageRoot, LinkOption.NOFOLLOW_LINKS)) {
                return List.of();
            }
            root = storageRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | RuntimeException exception) {
            logScanWarning("root", exception);
            return List.of();
        }

        try (DirectoryStream<Path> prefixes = Files.newDirectoryStream(root)) {
            for (Path prefix : prefixes) {
                if (candidates.size() >= limit) {
                    break;
                }
                scanPrefix(prefix, cutoff, limit, candidates);
            }
        } catch (IOException | RuntimeException exception) {
            logScanWarning("root-entries", exception);
        }
        return List.copyOf(candidates);
    }

    /** 删除扫描时 mtime 未变化的孤儿候选；失败最多重试三次。 */
    public DeleteResult deleteReconcileCandidate(ReconcileCandidate candidate) {
        if (candidate == null || candidate.lastModifiedTime() == null) {
            return DeleteResult.SKIPPED;
        }
        return deleteWithRetry(candidate.storageKey(), candidate.lastModifiedTime());
    }

    private void scanPrefix(
            Path prefix,
            Instant cutoff,
            int limit,
            List<ReconcileCandidate> candidates) {
        String prefixName = prefix.getFileName() == null ? "" : prefix.getFileName().toString();
        if (!prefixName.matches("[0-9a-f]{2}")
                || Files.isSymbolicLink(prefix)
                || !Files.isDirectory(prefix, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(prefix)) {
            for (Path file : files) {
                if (candidates.size() >= limit) {
                    return;
                }
                tryAddCandidate(prefixName, file, cutoff, candidates);
            }
        } catch (IOException | RuntimeException exception) {
            logScanWarning(prefixName, exception);
        }
    }

    private void tryAddCandidate(
            String prefixName,
            Path file,
            Instant cutoff,
            List<ReconcileCandidate> candidates) {
        String filename = file.getFileName() == null ? "" : file.getFileName().toString();
        String storageKey = prefixName + "/" + filename;
        if (!GENERATED_KEY_PATTERN.matcher(storageKey).matches()
                || Files.isSymbolicLink(file)) {
            return;
        }
        try {
            BasicFileAttributes attributes = candidateAttributesReader.read(file);
            if (!attributes.isRegularFile()
                    || attributes.lastModifiedTime().toInstant().isAfter(cutoff)) {
                return;
            }
            candidates.add(new ReconcileCandidate(storageKey, attributes.lastModifiedTime()));
        } catch (IOException | RuntimeException exception) {
            logScanWarning(storageKey, exception);
        }
    }

    private DeleteResult deleteWithRetry(String storageKey, FileTime expectedLastModified) {
        if (!isGeneratedStorageKey(storageKey)) {
            return DeleteResult.SKIPPED;
        }
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_DELETE_ATTEMPTS; attempt++) {
            Path candidate;
            try {
                candidate = resolveForDelete(storageKey);
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
                fingerprint(storageKey),
                MAX_DELETE_ATTEMPTS,
                lastFailure == null ? "unknown" : lastFailure.getClass().getSimpleName());
        return DeleteResult.FAILED;
    }

    private boolean isGeneratedStorageKey(String storageKey) {
        return storageKey != null && GENERATED_KEY_PATTERN.matcher(storageKey).matches();
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

    private void moveWithoutOverwrite(Path temporary, Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        try {
            moveOperation.move(temporary, target, true);
        } catch (AtomicMoveNotSupportedException unsupported) {
            if (!temporary.getParent().equals(target.getParent())) {
                throw new IOException("Complaint attachment fallback move escaped parent", unsupported);
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(target.toString());
            }
            moveOperation.move(temporary, target, false);
        }
    }

    private static void moveFile(Path source, Path target, boolean atomic) throws IOException {
        if (atomic) {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            return;
        }
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
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // 临时文件不进入孤儿附件回收；此处不掩盖原始上传失败。
        }
    }

    private Path resolveForWrite(String storageKey) throws IOException {
        Path normalized = resolveNormalized(storageKey);
        Path realRoot = Files.createDirectories(storageRoot).toRealPath();
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
        Path normalized = resolveNormalized(storageKey);
        if (Files.isSymbolicLink(storageRoot)
                || Files.isSymbolicLink(normalized.getParent())) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        Path realRoot = Files.createDirectories(storageRoot)
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
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
    interface DeleteOperation {
        boolean deleteIfExists(Path path) throws IOException;
    }

    @FunctionalInterface
    interface CandidateAttributesReader {
        BasicFileAttributes read(Path path) throws IOException;
    }
}
