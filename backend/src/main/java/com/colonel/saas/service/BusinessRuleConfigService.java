package com.colonel.saas.service;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BusinessRuleConfigService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Optional<String>> rawValueCache = new ConcurrentHashMap<>();

    public BusinessRuleConfigService(SystemConfigMapper systemConfigMapper, ObjectMapper objectMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.objectMapper = objectMapper;
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

    private String getRawValue(String configKey) {
        return rawValueCache.computeIfAbsent(configKey, this::loadRawValue)
                .orElse(null);
    }

    public void invalidate(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return;
        }
        rawValueCache.remove(configKey);
    }

    public void invalidateAll() {
        rawValueCache.clear();
    }

    private Optional<String> loadRawValue(String configKey) {
        return systemConfigMapper.findByConfigKey(configKey)
                .filter(config -> config.getDeleted() == null || config.getDeleted() == 0)
                .map(SystemConfig::getConfigValue);
    }

    private String normalizeLevel(Object rawLevel) {
        String level = String.valueOf(rawLevel).trim().toUpperCase();
        return StringUtils.hasText(level) ? level : null;
    }

    public record SampleDefaultStandardConfig(Long min30DaySales, String minLevel, Map<String, Object> raw) {
    }
}
