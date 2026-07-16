package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.talent.policy.ComplaintImagePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** 不对外静态暴露的达人投诉附件文件存储。 */
@Component
public class ComplaintAttachmentStorage {

    private final Path storageRoot;

    @Autowired
    public ComplaintAttachmentStorage(
            @Value("${talent.complaint.storage-root:/var/lib/colonel-saas/complaints}")
            String storageRoot) {
        this(Path.of(storageRoot));
    }

    public ComplaintAttachmentStorage(Path storageRoot) {
        if (storageRoot == null) {
            throw new IllegalArgumentException("storageRoot is required");
        }
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
    }

    public StoredAttachment store(ComplaintImagePolicy.ValidatedImage image) {
        if (image == null) {
            throw BusinessException.param("投诉图片不能为空");
        }
        String random = UUID.randomUUID().toString().replace("-", "");
        String storageKey = random.substring(0, 2) + "/" + random + "." + image.extension();
        try {
            Path target = resolveForWrite(storageKey);
            Files.write(target, image.content(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new StoredAttachment(
                    storageKey,
                    image.originalName(),
                    image.contentType(),
                    Files.size(target),
                    image.sha256());
        } catch (IOException exception) {
            deleteQuietly(storageKey);
            throw new IllegalStateException("投诉附件写入失败", exception);
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
            // 回滚清理不得掩盖原始业务/数据库异常。
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

    public record StoredAttachment(
            String storageKey,
            String originalName,
            String contentType,
            long fileSize,
            String sha256) {
    }
}
