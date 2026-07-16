package com.colonel.saas.domain.talent.policy;

import com.colonel.saas.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 投诉图片的数量、大小、文件名、MIME 与魔数联合校验。 */
@Component
public class ComplaintImagePolicy {

    public static final int MAX_FILES = 9;
    public static final int MAX_FILE_SIZE = 10 * 1024 * 1024;

    private static final Pattern SAFE_BASENAME =
            Pattern.compile("[\\p{L}\\p{N}_ -]+");
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
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw BusinessException.param("投诉图片不能是空文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.param("单张投诉图片不能超过 10MB");
        }
        String originalName = validateFilename(file.getOriginalFilename());
        String extension = extension(originalName);
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().trim().toLowerCase(Locale.ROOT);
        String expectedMime = EXPECTED_MIME.get(extension);
        if (!expectedMime.equals(contentType)) {
            throw BusinessException.param("投诉图片扩展名与 MIME 类型不一致");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw BusinessException.param("投诉图片读取失败", exception);
        }
        if (content.length == 0) {
            throw BusinessException.param("投诉图片不能是空文件");
        }
        if (content.length > MAX_FILE_SIZE) {
            throw BusinessException.param("单张投诉图片不能超过 10MB");
        }
        if (!magicMatches(extension, content)) {
            throw BusinessException.param("投诉图片文件格式与扩展名不一致");
        }
        String canonicalExtension = "jpeg".equals(extension) ? "jpg" : extension;
        return new ValidatedImage(
                originalName,
                expectedMime,
                canonicalExtension,
                content,
                sha256(content));
    }

    private String validateFilename(String originalName) {
        if (originalName == null || originalName.isBlank()
                || originalName.indexOf('/') >= 0
                || originalName.indexOf('\\') >= 0
                || originalName.indexOf('\0') >= 0) {
            throw BusinessException.param("投诉图片文件名不合法");
        }
        String name = originalName.trim();
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
            case "png" -> startsWith(content, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
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
        if (content.length < offset + expected.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if (unsigned(content[offset + index]) != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record ValidatedImage(
            String originalName,
            String contentType,
            String extension,
            byte[] content,
            String sha256) {
        public ValidatedImage {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
