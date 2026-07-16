package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.talent.policy.ComplaintImagePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplaintAttachmentStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storeAndLoad_shouldUseRandomRelativeKeyInsideProtectedRoot() {
        ComplaintAttachmentStorage storage = new ComplaintAttachmentStorage(tempDir);
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
        ComplaintImagePolicy.ValidatedImage image = new ComplaintImagePolicy.ValidatedImage(
                "proof.jpg", "image/jpeg", "jpg", content, "a".repeat(64));

        ComplaintAttachmentStorage.StoredAttachment stored = storage.store(image);

        assertThat(Path.of(stored.storageKey())).isRelative();
        assertThat(stored.storageKey()).doesNotContain("proof", tempDir.toString());
        assertThat(storage.load(stored.storageKey())).containsExactly(content);
    }

    @Test
    void load_shouldRejectTraversalAndAbsolutePaths() {
        ComplaintAttachmentStorage storage = new ComplaintAttachmentStorage(tempDir);

        assertThatThrownBy(() -> storage.load("../outside.jpg"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.load(tempDir.resolve("absolute.jpg").toString()))
                .isInstanceOf(BusinessException.class);
    }
}
