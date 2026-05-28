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

/**
 * 系统配置定义注册中心。
 * <p>
 * 集中管理所有系统配置项的元数据定义，包括值类型、是否允许运行时编辑、是否敏感、
 * 以及值校验规则。当配置值发生变更时，通过 {@link #validateOrThrow(String, String)} 方法
 * 强制执行类型和范围校验，防止无效配置进入系统。
 * </p>
 *
 * <p>已注册的配置域：</p>
 * <ul>
 *   <li><strong>寄样域</strong> —— 限制天数、自动关闭天数、默认寄样门槛（JSON）</li>
 *   <li><strong>达人域</strong> —— 保护期天数、独家达人阈值、预设标签库（JSON）</li>
 *   <li><strong>提成域</strong> —— 招商/渠道默认提成比例</li>
 *   <li><strong>推广域</strong> —— 复制讲解模板、pick_extra 生成规则（JSON）</li>
 *   <li><strong>安全域</strong> —— 登录失败锁定次数、锁定时长</li>
 * </ul>
 *
 * <p>校验策略：</p>
 * <ul>
 *   <li>数值型配置使用范围校验（{@link #requireRange} / {@link #requireDecimalRange}）</li>
 *   <li>JSON 型配置使用 Jackson 解析 + 业务规则校验（如预设标签库上限 50 项）</li>
 *   <li>布尔型配置只接受 true/false/1/0</li>
 *   <li>不允许运行时编辑的配置项调用校验时会抛出 {@link BusinessException}</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link SystemConfigKeys} —— 提供所有配置键名常量</li>
 *   <li>{@link RuleCenterSchemaRegistry} —— UI Schema 注册中心，查询配置元数据时回退到本类</li>
 *   <li>{@link ConfigChangedEventFactory} —— 变更事件工厂，使用本类推断值类型</li>
 * </ul>
 *
 * @see SystemConfigKeys
 * @see RuleCenterSchemaRegistry
 */
@Component
public class ConfigDefinitionRegistry {

    /** Jackson ObjectMapper，用于 JSON 型配置值的解析和校验 */
    private final ObjectMapper objectMapper;
    /** 所有已注册配置项的定义映射表（键为标准化后的小写配置键名） */
    private final Map<String, ConfigDefinition> definitions;

    /**
     * 构造函数，初始化注册中心并构建所有配置项定义。
     *
     * @param objectMapper Jackson ObjectMapper，用于 JSON 配置校验
     */
    public ConfigDefinitionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.definitions = buildDefinitions();
    }

    /**
     * 根据配置键查找配置定义。
     *
     * @param key 配置键名
     * @return 包含配置定义的 Optional，未注册时返回 empty
     */
    public Optional<ConfigDefinition> find(String key) {
        return Optional.ofNullable(definitions.get(normalize(key)));
    }

    /**
     * 校验配置值，不合法时抛出 {@link BusinessException}。
     * <p>
     * 若配置键未注册（无定义），则不做校验直接通过。
     * 已注册的配置项会执行类型检查和范围校验。
     * </p>
     *
     * @param key   配置键名
     * @param value 待校验的配置值
     * @throws BusinessException 配置值不合法时抛出
     */
    public void validateOrThrow(String key, String value) {
        find(key).ifPresent(definition -> definition.validate(value));
    }

    /**
     * 判断配置项是否为敏感信息。
     * <p>敏感信息在日志和 API 响应中应脱敏显示。未注册的配置键返回 false。</p>
     *
     * @param key 配置键名
     * @return 如果是敏感配置项返回 true
     */
    public boolean isSensitive(String key) {
        return find(key).map(ConfigDefinition::sensitive).orElse(false);
    }

    /**
     * 构建所有配置项定义。
     * <p>
     * 注册所有已知的系统配置键，为每个键指定值类型、是否允许运行时编辑、以及
     * 类型特定的校验逻辑。返回不可变 Map 以防止运行时修改。
     * </p>
     *
     * @return 配置键到配置定义的不可变映射
     */
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
        register(map, ConfigDefinition.decimal(
                SystemConfigKeys.MERCHANT_EXCLUSIVE_SERVICE_FEE_RATIO,
                true,
                value -> requireDecimalRange(value, 0D, 100D, "独家商家服务费占比阈值必须在 0~100 之间")
        ));
        register(map, ConfigDefinition.decimal(
                SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO,
                true,
                value -> requireDecimalRange(value, 0D, 1D, "招商默认提成比例必须在 0~1 之间")
        ));
        register(map, ConfigDefinition.decimal(
                SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO,
                true,
                value -> requireDecimalRange(value, 0D, 1D, "渠道默认提成比例必须在 0~1 之间")
        ));
        register(map, ConfigDefinition.json(
                SystemConfigKeys.SAMPLE_DEFAULT_STANDARD,
                true,
                this::validateSampleDefaultStandard
        ));
        register(map, ConfigDefinition.string(
                SystemConfigKeys.PROMOTION_COPY_BRIEF_TEMPLATE,
                true,
                value -> {
                    if (!StringUtils.hasText(value)) {
                        throw BusinessException.param("复制讲解模板不能为空");
                    }
                    if (value.length() > 4000) {
                        throw BusinessException.param("复制讲解模板不能超过 4000 字符");
                    }
                }
        ));
        register(map, ConfigDefinition.json(
                SystemConfigKeys.PROMOTION_PICK_EXTRA_RULE,
                true,
                this::validatePromotionPickExtraRule
        ));
        register(map, ConfigDefinition.json(
                SystemConfigKeys.PRESET_TALENT_TAGS,
                true,
                this::validatePresetTalentTags
        ));
        register(map, ConfigDefinition.integer(
                SystemConfigKeys.LOGIN_MAX_FAILURES,
                true,
                value -> requireRange(value, 1, 20, "登录失败锁定次数必须在 1~20 之间")
        ));
        register(map, ConfigDefinition.integer(
                SystemConfigKeys.LOGIN_LOCK_MINUTES,
                true,
                value -> requireRange(value, 1, 1440, "登录锁定时长必须在 1~1440 分钟之间")
        ));
        return Map.copyOf(map);
    }

    /**
     * 注册单个配置定义到映射表。
     *
     * @param map        目标映射表
     * @param definition 待注册的配置定义
     */
    private void register(Map<String, ConfigDefinition> map, ConfigDefinition definition) {
        map.put(normalize(definition.key()), definition);
    }

    /**
     * 标准化配置键：去除首尾空白并转小写，确保查找时大小写不敏感。
     *
     * @param key 原始配置键
     * @return 标准化后的配置键，null 视为空字符串
     */
    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 校验整数值是否在指定范围内。
     * <p>解析失败或超出范围时抛出 {@link BusinessException}。</p>
     *
     * @param value   待校验的字符串值
     * @param min     允许的最小值（含）
     * @param max     允许的最大值（含）
     * @param message 错误提示信息
     * @throws BusinessException 值不是合法整数或超出范围时抛出
     */
    private void requireRange(String value, int min, int max, String message) {
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (Exception ex) {
            throw BusinessException.param(message);
        }
        if (parsed < min || parsed > max) {
            throw BusinessException.param(message);
        }
    }

    /**
     * 校验小数值是否在指定范围内。
     * <p>解析失败或超出范围时抛出 {@link BusinessException}。</p>
     *
     * @param value   待校验的字符串值
     * @param min     允许的最小值（含）
     * @param max     允许的最大值（含）
     * @param message 错误提示信息
     * @throws BusinessException 值不是合法数字或超出范围时抛出
     */
    private void requireDecimalRange(String value, double min, double max, String message) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim());
        } catch (Exception ex) {
            throw BusinessException.param(message);
        }
        if (parsed < min || parsed > max) {
            throw BusinessException.param(message);
        }
    }

    /**
     * 校验达人预设标签库 JSON。
     * <p>校验规则：</p>
     * <ul>
     *   <li>必须是 JSON 数组</li>
     *   <li>最多 50 项</li>
     *   <li>每项不能为空字符串，长度不超过 24 字符</li>
     *   <li>使用 LinkedHashSet 检测重复（虽然结果不返回，但保证解析过程的一致性）</li>
     * </ul>
     *
     * @param raw JSON 字符串
     * @throws BusinessException 校验失败时抛出
     */
    private void validatePresetTalentTags(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray()) {
                throw BusinessException.param("达人预设标签库必须是 JSON 数组");
            }
            if (root.size() > 50) {
                throw BusinessException.param("达人预设标签库最多 50 项");
            }
            java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
            for (JsonNode node : root) {
                String tag = node == null || node.isNull() ? "" : node.asText("").trim();
                if (!StringUtils.hasText(tag)) {
                    throw BusinessException.param("达人预设标签不能为空字符串");
                }
                if (tag.length() > 24) {
                    throw BusinessException.param("达人预设标签长度不能超过 24 字符: " + tag);
                }
                unique.add(tag);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.param("达人预设标签库必须是合法 JSON 数组");
        }
    }

    /**
     * 校验推广 pick_extra 生成规则 JSON。
     * <p>校验规则：</p>
     * <ul>
     *   <li>必须是 JSON 对象</li>
     *   <li>{@code format} 字段不能为空，长度不超过 200 字符</li>
     *   <li>{@code encode} 字段仅支持 none/url/base64 三种值</li>
     * </ul>
     *
     * @param raw JSON 字符串
     * @throws BusinessException 校验失败时抛出
     */
    private void validatePromotionPickExtraRule(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isObject()) {
                throw BusinessException.param("pick_extra 规则必须是 JSON 对象");
            }
            String format = root.path("format").asText("").trim();
            if (!StringUtils.hasText(format)) {
                throw BusinessException.param("pick_extra 规则 format 不能为空");
            }
            if (format.length() > 200) {
                throw BusinessException.param("pick_extra 规则 format 不能超过 200 字符");
            }
            String encode = root.path("encode").asText("none").trim().toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(encode) && !encode.matches("none|url|base64")) {
                throw BusinessException.param("pick_extra 规则 encode 仅支持 none/url/base64");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.param("pick_extra 规则必须是合法 JSON 对象");
        }
    }

    /**
     * 校验寄样默认标准 JSON。
     * <p>校验规则：</p>
     * <ul>
     *   <li>必须是 JSON 对象</li>
     *   <li>{@code min_30day_sales}（如存在）必须为大于等于 0 的整数</li>
     *   <li>{@code min_level}（如存在）必须为 LV0/LV1/LV2 等格式</li>
     * </ul>
     *
     * @param raw JSON 字符串
     * @throws BusinessException 校验失败时抛出
     */
    private void validateSampleDefaultStandard(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isObject()) {
                throw BusinessException.param("寄样默认标准必须是 JSON 对象");
            }
            JsonNode salesNode = root.get("min_30day_sales");
            if (salesNode != null && !salesNode.isNull()) {
                if (!salesNode.canConvertToLong() || salesNode.longValue() < 0) {
                    throw BusinessException.param("寄样默认标准中的 min_30day_sales 必须是大于等于 0 的整数");
                }
            }
            JsonNode levelNode = root.get("min_level");
            if (levelNode != null && !levelNode.isNull()) {
                String level = levelNode.asText("").trim().toUpperCase(Locale.ROOT);
                if (StringUtils.hasText(level) && !level.matches("LV\\d+")) {
                    throw BusinessException.param("寄样默认标准中的 min_level 必须为 LV0/LV1/LV2 等格式");
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.param("寄样默认标准必须是合法 JSON");
        }
    }

    /**
     * 配置项定义记录。
     * <p>
     * 描述单个系统配置项的元数据，包括键名、值类型、是否允许运行时修改、
     * 是否敏感、以及值校验器。提供静态工厂方法快速创建不同类型配置定义。
     * </p>
     *
     * @param key             配置键名
     * @param valueType       配置值类型
     * @param runtimeEditable 是否允许运行时修改
     * @param sensitive       是否为敏感信息（日志/API 响应中需脱敏）
     * @param validator       值校验器，不合法时抛出 {@link BusinessException}
     */
    public record ConfigDefinition(
            String key,
            ConfigValueType valueType,
            boolean runtimeEditable,
            boolean sensitive,
            Consumer<String> validator
    ) {
        /**
         * 创建整数型配置定义。
         *
         * @param key             配置键名
         * @param runtimeEditable 是否允许运行时修改
         * @param validator       值校验器
         * @return 整数型配置定义
         */
        public static ConfigDefinition integer(String key, boolean runtimeEditable, Consumer<String> validator) {
            return new ConfigDefinition(key, ConfigValueType.INTEGER, runtimeEditable, false, validator);
        }

        /**
         * 创建小数型配置定义。
         *
         * @param key             配置键名
         * @param runtimeEditable 是否允许运行时修改
         * @param validator       值校验器
         * @return 小数型配置定义
         */
        public static ConfigDefinition decimal(String key, boolean runtimeEditable, Consumer<String> validator) {
            return new ConfigDefinition(key, ConfigValueType.DECIMAL, runtimeEditable, false, validator);
        }

        /**
         * 创建布尔型配置定义。
         * <p>内置校验器，只接受 true/false/1/0 四种值。</p>
         *
         * @param key             配置键名
         * @param runtimeEditable 是否允许运行时修改
         * @return 布尔型配置定义
         */
        public static ConfigDefinition bool(String key, boolean runtimeEditable) {
            return new ConfigDefinition(key, ConfigValueType.BOOLEAN, runtimeEditable, false, value -> {
                String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                if (!"true".equals(normalized) && !"false".equals(normalized)
                        && !"1".equals(normalized) && !"0".equals(normalized)) {
                    throw BusinessException.param("配置值必须是布尔值");
                }
            });
        }

        /**
         * 创建 JSON 型配置定义。
         *
         * @param key             配置键名
         * @param runtimeEditable 是否允许运行时修改
         * @param validator       JSON 业务校验器
         * @return JSON 型配置定义
         */
        public static ConfigDefinition json(String key, boolean runtimeEditable, Consumer<String> validator) {
            return new ConfigDefinition(key, ConfigValueType.JSON, runtimeEditable, false, validator);
        }

        /**
         * 创建字符串型配置定义。
         *
         * @param key             配置键名
         * @param runtimeEditable 是否允许运行时修改
         * @param validator       字符串校验器
         * @return 字符串型配置定义
         */
        public static ConfigDefinition string(String key, boolean runtimeEditable, Consumer<String> validator) {
            return new ConfigDefinition(key, ConfigValueType.STRING, runtimeEditable, false, validator);
        }

        /**
         * 校验配置值。
         * <p>
         * 首先检查配置项是否允许运行时修改，不允许则抛出异常。
         * 然后委托给具体的校验器执行类型和范围检查。
         * </p>
         *
         * @param value 待校验的配置值，null 被视为空字符串
         * @throws BusinessException 配置项不允许运行时修改或值校验失败时抛出
         */
        public void validate(String value) {
            if (!runtimeEditable) {
                throw BusinessException.stateInvalid("该配置项不允许运行时修改: " + key);
            }
            validator.accept(value == null ? "" : value);
        }
    }

    /**
     * 配置值类型枚举。
     * <p>
     * 定义系统支持的配置值数据类型，用于校验和事件溯源时的类型标记。
     * </p>
     *
     * <ul>
     *   <li>{@link #STRING} —— 纯文本字符串</li>
     *   <li>{@link #INTEGER} —— 整数</li>
     *   <li>{@link #DECIMAL} —— 小数（浮点数）</li>
     *   <li>{@link #BOOLEAN} —— 布尔值</li>
     *   <li>{@link #JSON} —— JSON 结构化数据</li>
     * </ul>
     */
    public enum ConfigValueType {
        /** 纯文本字符串 */
        STRING,
        /** 整数 */
        INTEGER,
        /** 小数（浮点数） */
        DECIMAL,
        /** 布尔值 */
        BOOLEAN,
        /** JSON 结构化数据 */
        JSON
    }
}
