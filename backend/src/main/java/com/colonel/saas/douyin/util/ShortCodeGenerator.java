package com.colonel.saas.douyin.util;

import java.util.UUID;

/**
 * 短码生成工具类。
 * <p>
 * 基于 UUID 的高位和低位比特异或后进行 Base36 编码，生成 8 位短码，
 * 用于 pick_source 等需要唯一且短小标识的场景。
 *
 * <ul>
 *   <li>随机生成 — 使用 UUID.randomUUID() 作为随机种子</li>
 *   <li>确定性生成 — 使用指定 UUID seed 保证可重现</li>
 *   <li>Base36 编码 — 8 位大写字母+数字组合，约 2.8 万亿种组合</li>
 * </ul>
 *
 * 所属业务领域：精选联盟 / 推广管理工具
 *
 * @see PromotionApi
 */
public final class ShortCodeGenerator {

    /** Base36 字母表：0-9 + A-Z */
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int RADIX = 36;
    private static final int SHORT_CODE_LENGTH = 8;
    private static final long MASK_48_BITS = 0xFFFFFFFFFFFFL;

    private ShortCodeGenerator() {
    }

    /**
     * 生成随机 ShortID（8 位 Base36）。
     * <p>
     * 内部使用 {@link UUID#randomUUID()} 作为随机种子，委托至 {@link #generate(UUID)}。
     *
     * @return 8 位 Base36 编码的短码
     */
    public static String generate() {
        return generate(UUID.randomUUID());
    }

    /**
     * 使用指定 UUID seed 生成 ShortID（8 位 Base36）。
     * <p>
     * 将 UUID 的高低 64 位异或后取低 48 位，再进行 Base36 编码，
     * 保证相同 UUID 总是生成相同的短码。
     *
     * @param uuid 用于生成短码的 UUID 种子
     * @return 8 位 Base36 编码的短码
     * @throws IllegalArgumentException uuid 为 null 时抛出
     */
    public static String generate(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid seed cannot be null");
        }
        // 第一步：取 UUID 高低位异或，压缩为 48 位随机种子
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        long combined = (msb ^ lsb) & MASK_48_BITS;
        // 第二步：将 48 位值编码为 8 位 Base36 字符串
        return encodeBase36(combined, SHORT_CODE_LENGTH);
    }

    /**
     * 将长整型值编码为定长 Base36 字符串。
     * <p>
     * 从低位到高位逐位取模，不足位数时左侧补零。
     *
     * @param value  待编码的长整型值
     * @param length 目标字符串长度
     * @return Base36 编码的定长字符串
     */
    private static String encodeBase36(long value, int length) {
        char[] chars = new char[length];
        for (int i = length - 1; i >= 0; i--) {
            chars[i] = ALPHABET.charAt((int) (value % RADIX));
            value /= RADIX;
        }
        return new String(chars);
    }
}
