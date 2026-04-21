package com.colonel.saas.douyin.util;

import java.util.UUID;

public final class ShortCodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int RADIX = 36;
    private static final int SHORT_CODE_LENGTH = 8;
    private static final long MASK_48_BITS = 0xFFFFFFFFFFFFL;

    private ShortCodeGenerator() {
    }

    /**
     * 生成随机 ShortID（8 位 Base36）。
     */
    public static String generate() {
        return generate(UUID.randomUUID());
    }

    /**
     * 使用指定 UUID seed 生成 ShortID（8 位 Base36）。
     */
    public static String generate(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid seed cannot be null");
        }
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        long combined = (msb ^ lsb) & MASK_48_BITS;
        return encodeBase36(combined, SHORT_CODE_LENGTH);
    }

    private static String encodeBase36(long value, int length) {
        char[] chars = new char[length];
        for (int i = length - 1; i >= 0; i--) {
            chars[i] = ALPHABET.charAt((int) (value % RADIX));
            value /= RADIX;
        }
        return new String(chars);
    }
}
