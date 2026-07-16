package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.talent.policy.ComplaintImagePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplaintAttachmentStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storeAndLoad_shouldStreamToRandomRelativeKeyInsideProtectedRoot() {
        ComplaintAttachmentStorage storage = new ComplaintAttachmentStorage(tempDir);
        byte[] content = jpeg(0x01);
        ComplaintImagePolicy.ValidatedImage image = new ComplaintImagePolicy().validate(List.of(
                new MockMultipartFile("files", "proof.jpg", "image/jpeg", content))).get(0);

        ComplaintAttachmentStorage.StoredAttachment stored = storage.store(image);

        assertThat(Path.of(stored.storageKey())).isRelative();
        assertThat(stored.storageKey()).matches("[0-9a-f]{2}/[0-9a-f]{32}\\.(jpg|png|webp)");
        assertThat(stored.storageKey()).doesNotContain("proof", tempDir.toString());
        assertThat(storage.load(stored.storageKey())).containsExactly(content);
        assertThat(stored.sha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void store_shouldDeleteTemporaryFileWhenSecondStreamChanges() throws Exception {
        ChangingMultipartFile file = new ChangingMultipartFile(jpeg(1), jpeg(2));
        ComplaintImagePolicy.ValidatedImage image = new ComplaintImagePolicy().validate(List.of(file)).get(0);
        ComplaintAttachmentStorage storage = new ComplaintAttachmentStorage(tempDir);

        assertThatThrownBy(() -> storage.store(image))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("变化");
        assertThat(countFiles()).isZero();
        assertThat(file.getBytesCalls()).isZero();
    }

    @Test
    void load_shouldRejectTraversalAndAbsolutePaths() {
        ComplaintAttachmentStorage storage = new ComplaintAttachmentStorage(tempDir);
        assertThatThrownBy(() -> storage.load("../outside.jpg"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.load(tempDir.resolve("absolute.jpg").toString()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void store_shouldFallbackWithinSameDirectoryWhenAtomicMoveIsUnsupported() {
        AtomicInteger moveCalls = new AtomicInteger();
        ComplaintAttachmentStorage storage = new ComplaintAttachmentStorage(
                tempDir,
                UUID::randomUUID,
                (source, target, atomic) -> {
                    moveCalls.incrementAndGet();
                    if (atomic) {
                        throw new AtomicMoveNotSupportedException(
                                source.toString(), target.toString(), "test filesystem");
                    }
                    assertThat(source.getParent()).isEqualTo(target.getParent());
                    Files.move(source, target);
                });
        byte[] content = jpeg(3);
        ComplaintImagePolicy.ValidatedImage image = new ComplaintImagePolicy().validate(List.of(
                new MockMultipartFile("files", "proof.jpg", "image/jpeg", content))).get(0);

        ComplaintAttachmentStorage.StoredAttachment stored = storage.store(image);

        assertThat(moveCalls).hasValue(2);
        assertThat(storage.load(stored.storageKey())).containsExactly(content);
    }

    @Test
    void store_shouldNeverOverwriteExistingTargetAndShouldCleanTemporaryFile() throws Exception {
        UUID fixed = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        Path target = tempDir.resolve("00/00112233445566778899aabbccddeeff.jpg");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[]{9, 8, 7});
        ComplaintAttachmentStorage storage = new ComplaintAttachmentStorage(
                tempDir,
                () -> fixed,
                (source, destination, atomic) -> Files.move(
                        source,
                        destination,
                        atomic ? new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE}
                                : new StandardCopyOption[]{}));
        ComplaintImagePolicy.ValidatedImage image = new ComplaintImagePolicy().validate(List.of(
                new MockMultipartFile("files", "proof.jpg", "image/jpeg", jpeg(4)))).get(0);

        assertThatThrownBy(() -> storage.store(image))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("写入失败");
        assertThat(Files.readAllBytes(target)).containsExactly(9, 8, 7);
        assertThat(countFiles()).isEqualTo(1);
    }

    private long countFiles() throws IOException {
        try (var paths = Files.walk(tempDir)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static byte[] jpeg(int tail) {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) tail};
    }

    private static final class ChangingMultipartFile implements MultipartFile {
        private final byte[][] versions;
        private final AtomicInteger streams = new AtomicInteger();
        private final AtomicInteger getBytes = new AtomicInteger();

        private ChangingMultipartFile(byte[] first, byte[] second) {
            versions = new byte[][]{first, second};
        }
        @Override public String getName() { return "files"; }
        @Override public String getOriginalFilename() { return "proof.jpg"; }
        @Override public String getContentType() { return "image/jpeg"; }
        @Override public boolean isEmpty() { return false; }
        @Override public long getSize() { return versions[0].length; }
        @Override public byte[] getBytes() { getBytes.incrementAndGet(); throw new AssertionError("getBytes forbidden"); }
        @Override public InputStream getInputStream() {
            int index = Math.min(streams.getAndIncrement(), versions.length - 1);
            return new ByteArrayInputStream(versions[index]);
        }
        @Override public void transferTo(File dest) throws IOException { Files.copy(getInputStream(), dest.toPath()); }
        int getBytesCalls() { return getBytes.get(); }
    }
}
