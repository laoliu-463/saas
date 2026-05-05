package com.colonel.saas.config;

import com.colonel.saas.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Component
public class ConfigDefinitionRegistry {

    private final ObjectMapper objectMapper;
    private final Map<String, ConfigDefinition> definitions;

    public ConfigDefinitionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.definitions = buildDefinitions();
    }

    public Optional<ConfigDefinition> find(String key) {
        return Optional.ofNullable(definitions.get(normalize(key)));
    }

    public void validateOrThrow(String key, String value) {
        find(key).ifPresent(definition -> definition.validate(value));
    }

    public boolean isSensitive(String key) {
        return find(key).map(ConfigDefinition::sensitive).orElse(false);
    }

    private Map<String, ConfigDefinition> buildDefinitions() {
        Map<String, ConfigDefinition> map = new LinkedHashMap<>();
        register(map, ConfigDefinition.integer(
                SystemConfigKeys.SAMPLE_RESTRICT_DAYS,
                true,
                value -> requireRange(value, 0, 365, "寄样限制天数必须在 0~365 之间")
        ));
        register(map, ConfigDefinition.bool(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED, true));
        register(map, ConfigDefinition.integer(
                SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS,
                true,
                value -> requireRange(value, 1, 365, "待交作业自动关闭天数必须在 1~365 之间")
        ));
        register(map, ConfigDefinition.integer(
                SystemConfigKeys.SAMPLE_TIMEOUT_PENDING_SHIP_DAYS,
                true,
                value -> requireRange(value, 1, 365, "待发货自动关闭天数必须在 1~365 之间")
        ));
        register(map, ConfigDefinition.integer(
                SystemConfigKeys.TALENT_PROTECTION_DAYS,
                true,
                value -> requireRange(value, 1, 365, "达人保护期天数必须在 1~365 之间")
        ));
        register(map, ConfigDefinition.decimal(
                SystemConfigKeys.TALENT_EXCLUSIVE_RATIO,
                true,
                value -> requireDecimalRange(value, 0D, 100D, "独家达人服务费占比阈值必须在 0~100 之间")
        ));
        register(map, ConfigDefinition.integer(
                SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES,
                true,
                value -> requireRange(value, 0, 10000, "独家达人月寄样数量阈值必须在 0~10000 之间")
        ));
        register(map, ConfigDefinition.json(
                SystemConfigKeys.SAMPLE_DEFAULT_STANDARD,
                true,
                this::validateSampleDefaultStandard
        ));
        return Map.copyOf(map);
    }

    private void register(Map<String, ConfigDefinition> map, ConfigDefinition definition) {
        map.put(normalize(definition.key()), definition);
    }

    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private void requireRange(String value, int min, int max, String message) {
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (Exception ex) {
            throw new BusinessException(message);
        }
        if (parsed < min || parsed > max) {
            throw new BusinessException(message);
        }
    }

    private void requireDecimalRange(String value, double min, double max, String message) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim());
        } catch (Exception ex) {
            throw new BusinessException(message);
        }
        if (parsed < min || parsed > max) {
            throw new BusinessException(message);
        }
    }

    private void validateSampleDefaultStandard(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isObject()) {
                throw new BusinessException("寄样默认标准必须是 JSON 对象");
            }
            JsonNode salesNode = root.get("min_30day_sales");
            if (salesNode != null && !salesNode.isNull()) {
                if (!salesNode.canConvertToLong() || salesNode.longValue() < 0) {
                    throw new BusinessException("寄样默认标准中的 min_30day_sales 必须是大于等于 0 的整数");
                }
            }
            JsonNode levelNode = root.get("min_level");
            if (levelNode != null && !levelNode.isNull()) {
                String level = levelNode.asText("").trim().toUpperCase(Locale.ROOT);
                if (StringUtils.hasText(level) && !level.matches("LV\\d+")) {
                    throw new BusinessException("寄样默认标准中的 min_level 必须为 LV0/LV1/LV2 等格式");
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("寄样默认标准必须是合法 JSON");
        }
    }

    public record ConfigDefinition(
            String key,
            ConfigValueType valueType,
            boolean runtimeEditable,
            boolean sensitive,
            Consumer<String> validator
    ) {
        public static ConfigDefinition integer(String key, boolean runtimeEditable, Consumer<String> validator) {
            return new ConfigDefinition(key, ConfigValueType.INTEGER, runtimeEditable, false, validator);
        }

        public static ConfigDefinition decimal(String key, boolean runtimeEditable, Consumer<String> validator) {
            return new ConfigDefinition(key, ConfigValueType.DECIMAL, runtimeEditable, false, validator);
        }

        public static ConfigDefinition bool(String key, boolean runtimeEditable) {
            return new ConfigDefinition(key, ConfigValueType.BOOLEAN, runtimeEditable, false, value -> {
                String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                if (!"true".equals(normalized) && !"false".equals(normalized)
                        && !"1".equals(normalized) && !"0".equals(normalized)) {
                    throw new BusinessException("配置值必须是布尔值");
                }
            });
        }

        public static ConfigDefinition json(String key, boolean runtimeEditable, Consumer<String> validator) {
            return new ConfigDefinition(key, ConfigValueType.JSON, runtimeEditable, false, validator);
        }

        public void validate(String value) {
            if (!runtimeEditable) {
                throw new BusinessException("该配置项不允许运行时修改: " + key);
            }
            validator.accept(value == null ? "" : value);
        }
    }

    public enum ConfigValueType {
        STRING,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        JSON
    }
}
