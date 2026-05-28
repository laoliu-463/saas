package com.colonel.saas.service;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 业务规则配置服务。
 *
 * <p>职责：从数据库（system_config 表）读取可配置的业务规则参数，并提供带本地缓存的访问接口。
 * 运维人员可在不重启服务的情况下动态调整业务规则阈值。
 *
 * <p>提供的配置项包括：
 * <ul>
 *   <li>达人保护期天数、独家达人判定阈值</li>
 *   <li>寄样限制天数、寄样超时天数</li>
 *   <li>默认寄样标准（30日销量、等级要求）</li>
 *   <li>转链额外规则（格式、编码）</li>
 *   <li>预设达人标签列表</li>
 *   <li>登录失败次数限制、锁定时长</li>
 *   <li>推广文案模板</li>
 * </ul>
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link SystemConfigMapper} —— 系统配置数据访问</li>
 *   <li>{@link ShortTtlCacheService} —— 短TTL本地缓存，避免频繁数据库查询</li>
 *   <li>{@link ObjectMapper} —— JSON 序列化/反序列化</li>
 * </ul>
 */
@Service
public class BusinessRuleConfigService {

    /** JSON 反序列化用的 Map 类型引用 */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    /** 配置缓存TTL：5分钟 */
    private static final Duration CONFIG_CACHE_TTL = Duration.ofMinutes(5);
    /** 缓存键前缀，用于隔离业务配置缓存 */
    private static final String CONFIG_CACHE_PREFIX = "biz-config:raw:";

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;
    private final ShortTtlCacheService shortTtlCacheService;

    public BusinessRuleConfigService(
            SystemConfigMapper systemConfigMapper,
            ObjectMapper objectMapper,
            ShortTtlCacheService shortTtlCacheService) {
        this.systemConfigMapper = systemConfigMapper;
        this.objectMapper = objectMapper;
        this.shortTtlCacheService = shortTtlCacheService;
    }

    /**
     * 获取达人保护期天数。
     * 在保护期内，达人归属不会被其他人覆盖。
     *
     * @return 保护期天数，默认 30 天
     */
    public int getTalentProtectionDays() {
        return getInt(SystemConfigKeys.TALENT_PROTECTION_DAYS, 30);
    }

    /**
     * 获取独家达人的佣金率阈值。
     * 当达人佣金率占比超过此阈值时，可被判定为独家达人。
     *
     * @return 佣金率阈值百分比，默认 70
     */
    public BigDecimal getTalentExclusiveRatioThreshold() {
        return getDecimal(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO, new BigDecimal("70"));
    }

    /**
     * 获取独家达人月度寄样数量要求。
     *
     * @return 每月寄样数量下限，默认 10
     */
    public int getTalentExclusiveMonthlySamples() {
        return getInt(SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES, 10);
    }

    /**
     * 获取寄样限制天数。
     * 在限制期内，同一达人不能重复申请同一商品的寄样。
     *
     * @return 限制天数，默认 7 天
     */
    public int getSampleRestrictDays() {
        return getInt(SystemConfigKeys.SAMPLE_RESTRICT_DAYS, 7);
    }

    /**
     * 判断寄样限制功能是否启用。
     *
     * @return true 表示启用寄样限制，默认 true
     */
    public boolean isSampleRestrictEnabled() {
        return getBoolean(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED, true);
    }

    /**
     * 获取寄样作业超时天数（待作业状态超时阈值）。
     *
     * @return 超时天数，默认 30 天
     */
    public int getSampleTimeoutHomeworkDays() {
        return getInt(SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS, 30);
    }

    /**
     * 获取寄样待发货超时天数。
     *
     * @return 超时天数，默认 15 天
     */
    public int getSampleTimeoutPendingShipDays() {
        return getInt(SystemConfigKeys.SAMPLE_TIMEOUT_PENDING_SHIP_DAYS, 15);
    }

    /**
     * 获取默认寄样标准配置（30日销量、达人等级等）。
     *
     * @return 寄样标准配置对象，若配置不存在则返回默认空值
     */
    public SampleDefaultStandardConfig getSampleDefaultStandard() {
        Map<String, Object> raw = getJson(SystemConfigKeys.SAMPLE_DEFAULT_STANDARD);
        Long min30DaySales = asLong(raw.get("min_30day_sales"));
        String minLevel = raw.get("min_level") == null ? null : normalizeLevel(raw.get("min_level"));
        return new SampleDefaultStandardConfig(min30DaySales, minLevel, raw);
    }

    /**
     * 获取推广文案模板。
     * 模板支持 {productName}、{commissionRate}、{shortLink} 等占位符。
     *
     * @return 推广文案模板字符串
     */
    public String getPromotionCopyBriefTemplate() {
        String raw = getRawValue(SystemConfigKeys.PROMOTION_COPY_BRIEF_TEMPLATE);
        if (!StringUtils.hasText(raw)) {
            return "【{productName}】\n佣金率：{commissionRate}\n短链：{shortLink}";
        }
        return raw.trim();
    }

    /**
     * 获取推广转链额外规则配置。
     * 包含转链格式（format）和编码方式（encode）。
     *
     * @return 转链额外规则配置对象
     */
    public PromotionPickExtraRuleConfig getPromotionPickExtraRule() {
        Map<String, Object> raw = getJson(SystemConfigKeys.PROMOTION_PICK_EXTRA_RULE);
        String format = asText(raw.get("format"));
        String encode = asText(raw.get("encode"));
        if (!StringUtils.hasText(format)) {
            format = "channel_{channel_code}";
        }
        if (!StringUtils.hasText(encode)) {
            encode = "none";
        }
        return new PromotionPickExtraRuleConfig(format.trim(), encode.trim().toLowerCase(), raw);
    }

    /**
     * 获取预设的达人标签列表。
     * 从配置中读取 JSON 数组格式的标签列表，自动去重并保持顺序。
     *
     * @return 不可变的标签列表，配置不存在或解析失败时返回空列表
     */
    public List<String> getPresetTalentTags() {
        String raw = getRawValue(SystemConfigKeys.PRESET_TALENT_TAGS);
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(raw.trim(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            if (tags == null || tags.isEmpty()) {
                return List.of();
            }
            java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
            for (String tag : tags) {
                if (StringUtils.hasText(tag)) {
                    unique.add(tag.trim());
                }
            }
            return List.copyOf(unique);
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * 获取登录最大失败次数。
     * 超过此次数后账户将被锁定。
     *
     * @return 最大失败次数，默认 5
     */
    public int getLoginMaxFailures() {
        return getInt(SystemConfigKeys.LOGIN_MAX_FAILURES, 5);
    }

    /**
     * 获取登录锁定时长（分钟）。
     *
     * @return 锁定时长分钟数，默认 15
     */
    public int getLoginLockMinutes() {
        return getInt(SystemConfigKeys.LOGIN_LOCK_MINUTES, 15);
    }

    private int getInt(String configKey, int defaultValue) {
        String raw = getRawValue(configKey);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean getBoolean(String configKey, boolean defaultValue) {
        String raw = getRawValue(configKey);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        return defaultValue;
    }

    private BigDecimal getDecimal(String configKey, BigDecimal defaultValue) {
        String raw = getRawValue(configKey);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Map<String, Object> getJson(String configKey) {
        String raw = getRawValue(configKey);
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw.trim(), MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String getRawValue(String configKey) {
        return shortTtlCacheService.get(
                CONFIG_CACHE_PREFIX + configKey,
                CONFIG_CACHE_TTL,
                () -> loadRawValue(configKey).orElse(null));
    }

    /**
     * 使指定配置键的缓存失效。
     * 配置变更后应调用此方法以确保下次读取时获取最新值。
     *
     * @param configKey 配置键
     */
    public void invalidate(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return;
        }
        shortTtlCacheService.evict(CONFIG_CACHE_PREFIX + configKey);
    }

    /**
     * 使所有业务配置缓存失效。
     * 清除所有以 biz-config:raw: 为前缀的本地缓存条目。
     */
    public void invalidateAll() {
        shortTtlCacheService.evictLocalByPrefix(CONFIG_CACHE_PREFIX);
    }

    private Optional<String> loadRawValue(String configKey) {
        return systemConfigMapper.findByConfigKey(configKey)
                .filter(config -> config.getDeleted() == null || config.getDeleted() == 0)
                .map(SystemConfig::getConfigValue);
    }

    private String normalizeLevel(Object rawLevel) {
        return String.valueOf(rawLevel).trim().toUpperCase();
    }

    /**
     * 默认寄样标准配置。
     *
     * @param min30DaySales 最低30日销量
     * @param minLevel      最低达人等级
     * @param raw           原始配置 Map，用于扩展字段访问
     */
    public record SampleDefaultStandardConfig(Long min30DaySales, String minLevel, Map<String, Object> raw) {
    }

    /**
     * 推广转链额外规则配置。
     *
     * @param format 转链格式模板（如 channel_{channel_code}）
     * @param encode 编码方式（none / base64 等）
     * @param raw    原始配置 Map
     */
    public record PromotionPickExtraRuleConfig(String format, String encode, Map<String, Object> raw) {
    }
}
