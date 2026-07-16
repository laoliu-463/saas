package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.talent.policy.ComplaintImagePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** 不对外静态暴露的达人投诉附件文件存储。 */
@Component
public class ComplaintAttachmentStorage {

    private static final int BUFFER_SIZE = 64 * 1024;

    private final Path storageRoot;
    private final Supplier<UUID> uuidSupplier;
    private final MoveOperation moveOperation;

    @Autowired
    public ComplaintAttachmentStorage(
            @Value("${talent.complaint.storage-root:/var/lib/colonel-saas/complaints}")
            String storageRoot) {
        this(Path.of(storageRoot));
    }

    public ComplaintAttachmentStorage(Path storageRoot) {
        this(storageRoot, UUID::randomUUID, ComplaintAttachmentStorage::moveFile);
    }

    ComplaintAttachmentStorage(
            Path storageRoot,
            Supplier<UUID> uuidSupplier,
            MoveOperation moveOperation) {
        if (storageRoot == null) {
            throw new IllegalArgumentException("storageRoot is required");
        }
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
        this.moveOperation = Objects.requireNonNull(moveOperation, "moveOperation");
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
        try {
            Path candidate = resolveExisting(storageKey);
            Files.deleteIfExists(candidate);
        } catch (RuntimeException | IOException ignored) {
            // I5 将替换为有界重试与结构化告警。
        }
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

    private void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // 对账任务会清理超过 grace 的临时残留，不掩盖原始上传失败。
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

    @FunctionalInterface
    interface MoveOperation {
        void move(Path source, Path target, boolean atomic) throws IOException;
    }
}
