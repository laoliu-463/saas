package com.colonel.saas.domain.talent.policy;

import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplaintImagePolicyTest {

    private final ComplaintImagePolicy policy = new ComplaintImagePolicy();

    @Test
    void validate_shouldAcceptJpegPngAndWebpAndComputeSha256() {
        List<ComplaintImagePolicy.ValidatedImage> images = policy.validate(List.of(
                file("evidence.jpg", "image/jpeg", jpeg()),
                file("evidence.png", "image/png", png()),
                file("evidence.webp", "image/webp", webp())));

        assertThat(images).extracting(ComplaintImagePolicy.ValidatedImage::extension)
                .containsExactly("jpg", "png", "webp");
        assertThat(images).extracting(ComplaintImagePolicy.ValidatedImage::sha256)
                .allMatch(value -> value.matches("[0-9a-f]{64}"));
    }

    @Test
    void validate_shouldRejectMoreThanNineFilesBeforeReadingAnyFile() {
        List<MockMultipartFile> files = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            files.add(file("evidence" + index + ".jpg", "image/jpeg", jpeg()));
        }

        assertThatThrownBy(() -> policy.validate(files))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("9");
    }

    @Test
    void validate_shouldRejectFileLargerThanTenMegabytes() {
        byte[] oversized = new byte[ComplaintImagePolicy.MAX_FILE_SIZE + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;

        assertThatThrownBy(() -> policy.validate(List.of(
                file("large.jpg", "image/jpeg", oversized))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void validate_shouldRejectEmptyMismatchedAndDangerousFiles() {
        assertThatThrownBy(() -> policy.validate(List.of(
                file("empty.jpg", "image/jpeg", new byte[0]))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("空文件");

        assertThatThrownBy(() -> policy.validate(List.of(
                file("fake.jpg", "image/jpeg", png()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式");

        assertThatThrownBy(() -> policy.validate(List.of(
                file("double.png.jpg", "image/jpeg", jpeg()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件名");

        assertThatThrownBy(() -> policy.validate(List.of(
                file("../escape.jpg", "image/jpeg", jpeg()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件名");
    }

    @Test
    void validate_shouldRequireExtensionMimeAndMagicToAgree() {
        assertThatThrownBy(() -> policy.validate(List.of(
                file("evidence.png", "image/jpeg", png()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MIME");

        assertThatThrownBy(() -> policy.validate(List.of(
                file("evidence.webp", "image/webp", "RIFFxxxxNOPE".getBytes(StandardCharsets.US_ASCII)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式");
    }

    private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("files", name, contentType, bytes);
    }

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
    }

    private static byte[] webp() {
        return "RIFFxxxxWEBPdata".getBytes(StandardCharsets.US_ASCII);
    }
}
