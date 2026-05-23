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

@Service
public class BusinessRuleConfigService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Duration CONFIG_CACHE_TTL = Duration.ofMinutes(5);
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

    public int getTalentProtectionDays() {
        return getInt(SystemConfigKeys.TALENT_PROTECTION_DAYS, 30);
    }

    public BigDecimal getTalentExclusiveRatioThreshold() {
        return getDecimal(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO, new BigDecimal("70"));
    }

    public int getTalentExclusiveMonthlySamples() {
        return getInt(SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES, 10);
    }

    public int getSampleRestrictDays() {
        return getInt(SystemConfigKeys.SAMPLE_RESTRICT_DAYS, 7);
    }

    public boolean isSampleRestrictEnabled() {
        return getBoolean(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED, true);
    }

    public int getSampleTimeoutHomeworkDays() {
        return getInt(SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS, 30);
    }

    public int getSampleTimeoutPendingShipDays() {
        return getInt(SystemConfigKeys.SAMPLE_TIMEOUT_PENDING_SHIP_DAYS, 15);
    }

    public SampleDefaultStandardConfig getSampleDefaultStandard() {
        Map<String, Object> raw = getJson(SystemConfigKeys.SAMPLE_DEFAULT_STANDARD);
        Long min30DaySales = asLong(raw.get("min_30day_sales"));
        String minLevel = raw.get("min_level") == null ? null : normalizeLevel(raw.get("min_level"));
        return new SampleDefaultStandardConfig(min30DaySales, minLevel, raw);
    }

    public String getPromotionCopyBriefTemplate() {
        String raw = getRawValue(SystemConfigKeys.PROMOTION_COPY_BRIEF_TEMPLATE);
        if (!StringUtils.hasText(raw)) {
            return "【{productName}】\n佣金率：{commissionRate}\n短链：{shortLink}";
        }
        return raw.trim();
    }

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

    public void invalidate(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return;
        }
        shortTtlCacheService.evict(CONFIG_CACHE_PREFIX + configKey);
    }

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

    public record SampleDefaultStandardConfig(Long min30DaySales, String minLevel, Map<String, Object> raw) {
    }

    public record PromotionPickExtraRuleConfig(String format, String encode, Map<String, Object> raw) {
    }
}
