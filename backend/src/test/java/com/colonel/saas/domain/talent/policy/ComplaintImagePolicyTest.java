package com.colonel.saas.domain.talent.policy;

import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplaintImagePolicyTest {

    private final ComplaintImagePolicy policy = new ComplaintImagePolicy();

    @Test
    void validate_shouldAcceptJpegPngAndWebpAndKeepOnlyThirtyTwoByteHashes() {
        List<ComplaintImagePolicy.ValidatedImage> images = policy.validate(List.of(
                file("evidence.jpg", "image/jpeg", jpeg()),
                file("evidence.png", "image/png", png()),
                file("evidence.webp", "image/webp", webp())));

        assertThat(images).extracting(ComplaintImagePolicy.ValidatedImage::extension)
                .containsExactly("jpg", "png", "webp");
        assertThat(images).allSatisfy(image -> {
            assertThat(image.sha256()).hasSize(32);
            assertThat(image.actualSize()).isPositive();
            assertThat(image.source()).isNotNull();
        });
        assertThat(List.of(ComplaintImagePolicy.ValidatedImage.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .doesNotContain("content");
    }

    @Test
    void validate_shouldStreamNineTenMegabyteFilesWithoutCallingGetBytes() {
        List<StreamingMultipartFile> files = new ArrayList<>();
        for (int index = 0; index < ComplaintImagePolicy.MAX_FILES; index++) {
            files.add(new StreamingMultipartFile(
                    "evidence" + index + ".jpg",
                    "image/jpeg",
                    ComplaintImagePolicy.MAX_FILE_SIZE,
                    () -> new SizedJpegInputStream(ComplaintImagePolicy.MAX_FILE_SIZE)));
        }

        List<ComplaintImagePolicy.ValidatedImage> images = policy.validate(files);

        assertThat(images).hasSize(ComplaintImagePolicy.MAX_FILES)
                .allSatisfy(image -> assertThat(image.actualSize())
                        .isEqualTo(ComplaintImagePolicy.MAX_FILE_SIZE));
        assertThat(files).allSatisfy(file -> {
            assertThat(file.getBytesCalls()).isZero();
            assertThat(file.getInputStreamCalls()).isEqualTo(1);
        });
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
    void validate_shouldRejectDeclaredAndActualSizeLiesOrMidStreamOverflow() {
        StreamingMultipartFile declaredTooLarge = new StreamingMultipartFile(
                "large.jpg", "image/jpeg", ComplaintImagePolicy.MAX_FILE_SIZE + 1L,
                () -> new SizedJpegInputStream(4));
        assertThatThrownBy(() -> policy.validate(List.of(declaredTooLarge)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10MB");
        assertThat(declaredTooLarge.getInputStreamCalls()).isZero();

        StreamingMultipartFile sizeLie = new StreamingMultipartFile(
                "lie.jpg", "image/jpeg", 4, () -> new SizedJpegInputStream(5));
        assertThatThrownBy(() -> policy.validate(List.of(sizeLie)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大小");

        StreamingMultipartFile overflow = new StreamingMultipartFile(
                "overflow.jpg", "image/jpeg", ComplaintImagePolicy.MAX_FILE_SIZE,
                () -> new SizedJpegInputStream(ComplaintImagePolicy.MAX_FILE_SIZE + 1L));
        assertThatThrownBy(() -> policy.validate(List.of(overflow)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void validate_shouldNormalizeAndEnforcePostgresFilenameCodePointLimit() {
        String maxChinese = "名".repeat(251) + ".jpg";
        String supplementaryLetter = new String(Character.toChars(0x10400));
        String maxSupplementary = supplementaryLetter.repeat(251) + ".jpg";

        assertThat(policy.validate(List.of(file(maxChinese, "image/jpeg", jpeg()))))
                .singleElement().extracting(ComplaintImagePolicy.ValidatedImage::originalName)
                .isEqualTo(maxChinese);
        assertThat(policy.validate(List.of(file(maxSupplementary, "image/jpeg", jpeg()))))
                .singleElement().extracting(ComplaintImagePolicy.ValidatedImage::originalName)
                .isEqualTo(maxSupplementary);
        assertThat(policy.validate(List.of(file("  e\u0301vidence.jpg  ", "image/jpeg", jpeg()))))
                .singleElement().extracting(ComplaintImagePolicy.ValidatedImage::originalName)
                .isEqualTo("évidence.jpg");

        assertThatThrownBy(() -> policy.validate(List.of(
                file("名".repeat(252) + ".jpg", "image/jpeg", jpeg()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("255");
    }

    @Test
    void validate_shouldRejectEmptyMismatchedAndDangerousFiles() {
        assertThatThrownBy(() -> policy.validate(List.of(
                file("empty.jpg", "image/jpeg", new byte[0]))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("空文件");
        assertThatThrownBy(() -> policy.validate(List.of(
                file("fake.jpg", "image/jpeg", png()))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("格式");
        assertThatThrownBy(() -> policy.validate(List.of(
                file("double.png.jpg", "image/jpeg", jpeg()))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("文件名");
        assertThatThrownBy(() -> policy.validate(List.of(
                file("../escape.jpg", "image/jpeg", jpeg()))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("文件名");
        assertThatThrownBy(() -> policy.validate(List.of(
                file("evidence.png", "image/jpeg", png()))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("MIME");
        assertThatThrownBy(() -> policy.validate(List.of(
                file("evidence.webp", "image/webp", "RIFFxxxxNOPE".getBytes(StandardCharsets.US_ASCII)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("格式");
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

    private static final class SizedJpegInputStream extends InputStream {
        private final long size;
        private long position;

        private SizedJpegInputStream(long size) {
            this.size = size;
        }

        @Override
        public int read() {
            if (position >= size) {
                return -1;
            }
            int value = position < 3 ? new int[]{0xFF, 0xD8, 0xFF}[(int) position] : 0;
            position++;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (position >= size) {
                return -1;
            }
            int read = (int) Math.min(length, size - position);
            java.util.Arrays.fill(bytes, offset, offset + read, (byte) 0);
            for (int index = 0; index < read && position + index < 3; index++) {
                bytes[offset + index] = (byte) new int[]{0xFF, 0xD8, 0xFF}[(int) position + index];
            }
            position += read;
            return read;
        }
    }

    private static final class StreamingMultipartFile implements MultipartFile {
        private final String originalName;
        private final String contentType;
        private final long declaredSize;
        private final Supplier<InputStream> streams;
        private final AtomicInteger inputStreamCalls = new AtomicInteger();
        private final AtomicInteger getBytesCalls = new AtomicInteger();

        private StreamingMultipartFile(
                String originalName, String contentType, long declaredSize,
                Supplier<InputStream> streams) {
            this.originalName = originalName;
            this.contentType = contentType;
            this.declaredSize = declaredSize;
            this.streams = streams;
        }

        @Override public String getName() { return "files"; }
        @Override public String getOriginalFilename() { return originalName; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return declaredSize == 0; }
        @Override public long getSize() { return declaredSize; }
        @Override public byte[] getBytes() { getBytesCalls.incrementAndGet(); throw new AssertionError("getBytes forbidden"); }
        @Override public InputStream getInputStream() { inputStreamCalls.incrementAndGet(); return streams.get(); }
        @Override public void transferTo(File dest) throws IOException { Files.copy(getInputStream(), dest.toPath()); }
        int getInputStreamCalls() { return inputStreamCalls.get(); }
        int getBytesCalls() { return getBytesCalls.get(); }
    }
}
