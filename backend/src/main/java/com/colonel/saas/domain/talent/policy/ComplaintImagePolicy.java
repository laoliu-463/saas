package com.colonel.saas.domain.talent.policy;

import com.colonel.saas.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 投诉图片的数量、流式大小、文件名、MIME、魔数与摘要联合校验。 */
@Component
public class ComplaintImagePolicy {

    public static final int MAX_FILES = 9;
    public static final int MAX_FILE_SIZE = 10 * 1024 * 1024;
    public static final int MAX_ORIGINAL_NAME_CODE_POINTS = 255;

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAGIC_PREFIX_SIZE = 12;
    private static final Pattern SAFE_BASENAME = Pattern.compile("[\\p{L}\\p{N}_ -]+");
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5",
            "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5",
            "lpt6", "lpt7", "lpt8", "lpt9");
    private static final Map<String, String> EXPECTED_MIME = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    public List<ValidatedImage> validate(List<? extends MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        if (files.size() > MAX_FILES) {
            throw BusinessException.param("投诉图片最多上传 9 张");
        }
        List<ValidatedImage> validated = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            validated.add(validateOne(file));
        }
        return List.copyOf(validated);
    }

    private ValidatedImage validateOne(MultipartFile file) {
        if (file == null) {
            throw BusinessException.param("投诉图片不能是空文件");
        }
        long declaredSize = file.getSize();
        if (declaredSize > MAX_FILE_SIZE) {
            throw BusinessException.param("单张投诉图片不能超过 10MB");
        }
        String originalName = validateFilename(file.getOriginalFilename());
        String extension = extension(originalName);
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().strip().toLowerCase(Locale.ROOT);
        String expectedMime = EXPECTED_MIME.get(extension);
        if (!expectedMime.equals(contentType)) {
            throw BusinessException.param("投诉图片扩展名与 MIME 类型不一致");
        }

        StreamDigest digest = readAndDigest(file);
        if (digest.size() == 0) {
            throw BusinessException.param("投诉图片不能是空文件");
        }
        if (declaredSize != digest.size()) {
            throw BusinessException.param("投诉图片声明大小与实际大小不一致");
        }
        if (!magicMatches(extension, digest.magicPrefix())) {
            throw BusinessException.param("投诉图片文件格式与扩展名不一致");
        }
        String canonicalExtension = "jpeg".equals(extension) ? "jpg" : extension;
        return new ValidatedImage(
                originalName, expectedMime, canonicalExtension,
                digest.size(), digest.sha256(), file);
    }

    private StreamDigest readAndDigest(MultipartFile file) {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        byte[] magicPrefix = new byte[MAGIC_PREFIX_SIZE];
        int magicLength = 0;
        long actualSize = 0;
        try (InputStream input = file.getInputStream()) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                actualSize += read;
                if (actualSize > MAX_FILE_SIZE) {
                    throw BusinessException.param("单张投诉图片不能超过 10MB");
                }
                if (magicLength < MAGIC_PREFIX_SIZE) {
                    int copy = Math.min(read, MAGIC_PREFIX_SIZE - magicLength);
                    System.arraycopy(buffer, 0, magicPrefix, magicLength, copy);
                    magicLength += copy;
                }
                digest.update(buffer, 0, read);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw BusinessException.param("投诉图片读取失败", exception);
        }
        return new StreamDigest(
                actualSize, Arrays.copyOf(magicPrefix, magicLength), digest.digest());
    }

    private String validateFilename(String originalName) {
        if (originalName == null
                || originalName.indexOf('/') >= 0
                || originalName.indexOf('\\') >= 0
                || originalName.indexOf('\0') >= 0) {
            throw BusinessException.param("投诉图片文件名不合法");
        }
        String name = Normalizer.normalize(originalName.strip(), Normalizer.Form.NFC);
        if (name.isBlank()) {
            throw BusinessException.param("投诉图片文件名不合法");
        }
        if (name.codePointCount(0, name.length()) > MAX_ORIGINAL_NAME_CODE_POINTS) {
            throw BusinessException.param("投诉图片文件名最多 255 个字符");
        }
        int firstDot = name.indexOf('.');
        int lastDot = name.lastIndexOf('.');
        if (firstDot <= 0 || firstDot != lastDot || lastDot == name.length() - 1) {
            throw BusinessException.param("投诉图片文件名不合法，不允许双重扩展名");
        }
        String base = name.substring(0, lastDot);
        String extension = name.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        if (!SAFE_BASENAME.matcher(base).matches()
                || WINDOWS_RESERVED.contains(base.toLowerCase(Locale.ROOT))
                || !EXPECTED_MIME.containsKey(extension)) {
            throw BusinessException.param("投诉图片文件名或扩展名不合法");
        }
        return name;
    }

    private String extension(String name) {
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private boolean magicMatches(String extension, byte[] content) {
        return switch (extension) {
            case "jpg", "jpeg" -> content.length >= 3
                    && unsigned(content[0]) == 0xFF
                    && unsigned(content[1]) == 0xD8
                    && unsigned(content[2]) == 0xFF;
            case "png" -> startsWith(content,
                    new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "webp" -> content.length >= 12
                    && asciiAt(content, 0, "RIFF")
                    && asciiAt(content, 8, "WEBP");
            default -> false;
        };
    }

    private boolean startsWith(byte[] content, int[] expected) {
        if (content.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (unsigned(content[index]) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiAt(byte[] content, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (content.length < offset + bytes.length) {
            return false;
        }
        for (int index = 0; index < bytes.length; index++) {
            if (content[offset + index] != bytes[index]) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record StreamDigest(long size, byte[] magicPrefix, byte[] sha256) {
    }

    public record ValidatedImage(
            String originalName,
            String contentType,
            String extension,
            long actualSize,
            byte[] sha256,
            MultipartFile source) {
        public ValidatedImage {
            Objects.requireNonNull(source, "source");
            sha256 = sha256 == null ? new byte[0] : sha256.clone();
            if (sha256.length != 32) {
                throw new IllegalArgumentException("sha256 must contain 32 bytes");
            }
        }

        @Override
        public byte[] sha256() {
            return sha256.clone();
        }
    }
}
