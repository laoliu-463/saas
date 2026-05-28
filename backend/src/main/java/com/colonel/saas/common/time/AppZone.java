package com.colonel.saas.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 应用级时区工具类（统一使用 {@code Asia/Shanghai}）。
 *
 * <p>解决分布式系统中时间戳与时区转换的一致性问题。系统中所有涉及
 * 秒级/毫秒级时间戳与 {@link LocalDateTime} 之间的转换均通过此类完成，
 * 避免各处自行创建 {@link ZoneId} 导致的时区不一致风险。</p>
 *
 * <h3>与 Jackson 的一致性</h3>
 * <p>此工具类使用的 {@code Asia/Shanghai} 时区与 application.yml 中 Jackson 的
 * {@code time-zone} 配置保持一致，确保序列化/反序列化和数据库存储使用相同时区。</p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>抖音开放平台返回的秒级/毫秒级时间戳转换为 {@link LocalDateTime}</li>
 *   <li>将业务实体中的 {@link LocalDateTime} 转换为时间戳写入数据库或传递给外部系统</li>
 *   <li>MyBatis TypeHandler 中的时间戳转换</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 抖音 API 返回的秒级时间戳
 * LocalDateTime createTime = AppZone.fromEpochSecond(1713628800L);
 *
 * // 毫秒级时间戳（JavaScript Date.getTime() 等）
 * LocalDateTime updateTime = AppZone.fromEpochMilli(1713628800000L);
 *
 * // 将本地时间转回时间戳（用于传递给外部 API）
 * long timestamp = AppZone.toEpochSecond(order.getCreateTime());
 * }</pre>
 *
 * @see ZoneId#of(String) 时区标识符
 * @see Instant 时间戳表示
 */
public final class AppZone {

    /** 应用统一时区：Asia/Shanghai（UTC+8），与 Jackson 序列化配置保持一致 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 工具类禁止实例化 */
    private AppZone() {
    }

    /**
     * 将秒级 Unix 时间戳转换为 {@link LocalDateTime}。
     *
     * <p>典型场景：抖音开放平台 API 返回的 {@code create_time} 字段。</p>
     *
     * @param epochSecond 秒级 Unix 时间戳（如 1713628800）
     * @return 对应时区的 LocalDateTime
     */
    public static LocalDateTime fromEpochSecond(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZONE);
    }

    /**
     * 将毫秒级 Unix 时间戳转换为 {@link LocalDateTime}。
     *
     * <p>典型场景：JavaScript {@code Date.getTime()} 返回的毫秒时间戳，
     * 或 {@code System.currentTimeMillis()} 的返回值。</p>
     *
     * @param epochMilli 毫秒级 Unix 时间戳（如 1713628800000）
     * @return 对应时区的 LocalDateTime
     */
    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZONE);
    }

    /**
     * 将 {@link LocalDateTime} 转换为秒级 Unix 时间戳。
     *
     * <p>输入为 null 时返回 0L（安全回退），避免 NullPointerException。</p>
     *
     * @param dateTime 待转换的 LocalDateTime，可为 null
     * @return 秒级 Unix 时间戳；输入为 null 时返回 0L
     */
    public static long toEpochSecond(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0L;
        }
        return dateTime.atZone(ZONE).toEpochSecond();
    }
}
