package com.colonel.saas.config;

import com.colonel.saas.config.ConfigDefinitionRegistry.ConfigDefinition;
import com.colonel.saas.config.ConfigDefinitionRegistry.ConfigValueType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 规则中心 UI Schema 注册中心。
 * <p>
 * 为前端规则中心页面提供配置项的 UI 元数据，包括分组、显示标签、值类型、
 * 取值范围、单位、消费域等信息。同时提供提成比例的业务校验（合计不超过 100%）。
 * </p>
 *
 * <p>注册的规则分组：</p>
 * <ul>
 *   <li><strong>sample</strong>（寄样规则）—— 寄样限制开关、重复申请限制天数、自动关闭天数、默认寄样门槛</li>
 *   <li><strong>talent</strong>（达人规则）—— 达人保护期、预设标签库</li>
 *   <li><strong>exclusive</strong>（独家规则）—— 独家达人/商家判定阈值（V2 预留）</li>
 *   <li><strong>commission</strong>（提成规则）—— 招商/渠道默认提成比例</li>
 *   <li><strong>promotion</strong>（推广规则）—— 复制讲解模板、pick_extra 生成规则</li>
 *   <li><strong>security</strong>（安全规则）—— 登录失败锁定策略</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link ConfigDefinitionRegistry} —— 配置定义注册中心，提供值校验和类型推断的后备</li>
 *   <li>{@link ConfigConsumerDomain} —— 消费域枚举，标识每个配置项影响的业务模块</li>
 *   <li>{@link ConfigChangedEventFactory} —— 变更事件工厂，从本类获取分组和消费域元数据</li>
 *   <li>{@link SystemConfigKeys} —— 配置键名常量</li>
 * </ul>
 *
 * @see ConfigDefinitionRegistry
 * @see ConfigConsumerDomain
 */
@Component
public class RuleCenterSchemaRegistry {

    /** 配置定义注册中心，提供值类型推断的后备来源 */
    private final ConfigDefinitionRegistry configDefinitionRegistry;
    /** 规则分组映射表（键为标准化后的分组编码） */
    private final Map<String, RuleGroupSchema> groups;
    /** 配置项映射表（键为标准化后的配置键名），用于按配置键快速查找 */
    private final Map<String, RuleItemSchema> itemsByKey;

    /**
     * 构造函数，初始化 Schema 注册中心。
     * <p>构建过程中将所有规则分组和配置项注册到内部映射表中。</p>
     *
     * @param configDefinitionRegistry 配置定义注册中心，用于值类型推断
     */
    public RuleCenterSchemaRegistry(ConfigDefinitionRegistry configDefinitionRegistry) {
        this.configDefinitionRegistry = configDefinitionRegistry;
        var built = buildSchema();
        this.groups = built.groups();
        this.itemsByKey = built.itemsByKey();
    }

    /**
     * 获取所有规则分组列表（防御性拷贝）。
     *
     * @return 规则分组的不可变列表
     */
    public List<RuleGroupSchema> groups() {
        return List.copyOf(groups.values());
    }

    /**
     * 根据配置键查找配置项 Schema。
     *
     * @param key 配置键名
     * @return 包含配置项 Schema 的 Optional，未注册时返回 empty
     */
    public Optional<RuleItemSchema> findItem(String key) {
        return Optional.ofNullable(itemsByKey.get(normalize(key)));
    }

    /**
     * 根据配置键查找配置定义（委托给 {@link ConfigDefinitionRegistry}）。
     * <p>当 Schema 注册中心未包含某配置键时，回退到配置定义注册中心。</p>
     *
     * @param key 配置键名
     * @return 包含配置定义的 Optional
     */
    public Optional<ConfigDefinition> findDefinition(String key) {
        return configDefinitionRegistry.find(key);
    }

    /**
     * 根据分组编码查找规则分组。
     *
     * @param groupCode 分组编码（如 "sample"、"talent"）
     * @return 包含规则分组的 Optional
     */
    public Optional<RuleGroupSchema> findGroup(String groupCode) {
        return Optional.ofNullable(groups.get(normalize(groupCode)));
    }

    /**
     * 获取指定分组下的所有配置项列表。
     *
     * @param groupCode 分组编码
     * @return 该分组下的配置项列表，分组不存在时返回空列表
     */
    public List<RuleItemSchema> itemsInGroup(String groupCode) {
        RuleGroupSchema group = groups.get(normalize(groupCode));
        return group == null ? List.of() : group.items();
    }

    /**
     * 获取指定分组下的所有配置键集合。
     *
     * @param groupCode 分组编码
     * @return 该分组下所有配置键的集合
     */
    public Set<String> keysInGroup(String groupCode) {
        return itemsInGroup(groupCode).stream().map(RuleItemSchema::key).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 标准化字符串值：去除首尾空白并转小写。
     *
     * @param value 原始字符串
     * @return 标准化后的字符串，null 视为空字符串
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 构建完整的规则 Schema。
     * <p>
     * 注册所有规则分组及其配置项。每个分组包含一组相关配置项，
     * 配置项带有 UI 显示标签、值类型、取值范围、消费域等元数据。
     * </p>
     *
     * @return 包含分组映射和配置项映射的 BuiltSchema 记录
     */
    private BuiltSchema buildSchema() {
        Map<String, RuleGroupSchema> groupMap = new LinkedHashMap<>();
        Map<String, RuleItemSchema> itemMap = new LinkedHashMap<>();

        registerGroup(groupMap, itemMap, group(
                "sample",
                "寄样规则",
                "控制寄样重复申请、默认寄样门槛",
                item(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED, "寄样限制开关", ConfigValueType.BOOLEAN,
                        List.of(ConfigConsumerDomain.SAMPLE), true, null, null, null, null),
                item(SystemConfigKeys.SAMPLE_RESTRICT_DAYS, "重复申请限制天数", ConfigValueType.INTEGER,
                        List.of(ConfigConsumerDomain.SAMPLE), true, 1, 90, "天", null),
                item(SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS, "待交作业自动关闭天数", ConfigValueType.INTEGER,
                        List.of(ConfigConsumerDomain.SAMPLE), false, 1, 365, "天",
                        "V2 预留规则，当前不参与实际计算"),
                item(SystemConfigKeys.SAMPLE_TIMEOUT_PENDING_SHIP_DAYS, "待发货自动关闭天数", ConfigValueType.INTEGER,
                        List.of(ConfigConsumerDomain.SAMPLE), true, 1, 365, "天", null),
                item(SystemConfigKeys.SAMPLE_DEFAULT_STANDARD, "默认寄样门槛", ConfigValueType.JSON,
                        List.of(ConfigConsumerDomain.SAMPLE, ConfigConsumerDomain.PRODUCT), true
                )));

        registerGroup(groupMap, itemMap, group(
                "talent",
                "达人规则",
                "达人认领保护期与标签预设库",
                item(SystemConfigKeys.TALENT_PROTECTION_DAYS, "达人保护期", ConfigValueType.INTEGER,
                        List.of(ConfigConsumerDomain.TALENT), true, 1, 365, "天", null),
                item(SystemConfigKeys.PRESET_TALENT_TAGS, "达人预设标签库", ConfigValueType.JSON,
                        List.of(ConfigConsumerDomain.TALENT), true)));

        registerGroup(groupMap, itemMap, group(
                "exclusive",
                "独家规则",
                "独家达人/商家判定阈值",
                item(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO, "独家达人服务费占比阈值", ConfigValueType.DECIMAL,
                        List.of(ConfigConsumerDomain.TALENT), false, 0D, 100D, "%",
                        "V2 预留规则，当前不参与实际计算"),
                item(SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES, "独家达人月寄样数量阈值", ConfigValueType.INTEGER,
                        List.of(ConfigConsumerDomain.TALENT), false, 0, 10000, "件",
                        "V2 预留规则，当前不参与实际计算"),
                item(SystemConfigKeys.MERCHANT_EXCLUSIVE_SERVICE_FEE_RATIO, "独家商家服务费占比阈值", ConfigValueType.DECIMAL,
                        List.of(ConfigConsumerDomain.PERFORMANCE), false, 0D, 100D, "%",
                        "V2 预留规则，当前不参与实际计算")));

        registerGroup(groupMap, itemMap, group(
                "commission",
                "提成规则",
                "全局招商/渠道默认提成比例",
                item(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO, "招商提成比例", ConfigValueType.DECIMAL,
                        List.of(ConfigConsumerDomain.PERFORMANCE), true, 0D, 1D, null,
                        "变更只影响后续计算或手动重算，历史业绩不会自动变化"),
                item(SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO, "渠道提成比例", ConfigValueType.DECIMAL,
                        List.of(ConfigConsumerDomain.PERFORMANCE), true, 0D, 1D, null,
                        "变更只影响后续计算或手动重算，历史业绩不会自动变化")));

        registerGroup(groupMap, itemMap, group(
                "promotion",
                "推广规则",
                "复制讲解模板与 pick_extra 生成规则",
                item(SystemConfigKeys.PROMOTION_COPY_BRIEF_TEMPLATE, "复制讲解模板", ConfigValueType.STRING,
                        List.of(ConfigConsumerDomain.PRODUCT), null),
                item(SystemConfigKeys.PROMOTION_PICK_EXTRA_RULE, "pick_extra 生成规则", ConfigValueType.JSON,
                        List.of(ConfigConsumerDomain.PRODUCT), null)));

        registerGroup(groupMap, itemMap, group(
                "security",
                "安全规则",
                "登录失败锁定策略",
                item(SystemConfigKeys.LOGIN_MAX_FAILURES, "登录失败锁定次数", ConfigValueType.INTEGER,
                        List.of(ConfigConsumerDomain.USER), true, 1, 20, "次", null),
                item(SystemConfigKeys.LOGIN_LOCK_MINUTES, "登录锁定时长", ConfigValueType.INTEGER,
                        List.of(ConfigConsumerDomain.USER), true, 1, 1440, "分钟", null)));

        return new BuiltSchema(Map.copyOf(groupMap), Map.copyOf(itemMap));
    }

    /**
     * 注册单个规则分组到映射表，并同时注册其下属的所有配置项。
     *
     * @param groupMap 分组映射表
     * @param itemMap  配置项映射表
     * @param group    待注册的规则分组
     */
    private void registerGroup(
            Map<String, RuleGroupSchema> groupMap,
            Map<String, RuleItemSchema> itemMap,
            RuleGroupSchema group) {
        groupMap.put(normalize(group.groupCode()), group);
        for (RuleItemSchema item : group.items()) {
            itemMap.put(normalize(item.key()), item);
        }
    }

    /**
     * 创建规则分组。
     *
     * @param code        分组编码（如 "sample"、"commission"）
     * @param name        分组显示名称
     * @param description 分组描述
     * @param items       该分组下的配置项
     * @return 规则分组记录
     */
    private RuleGroupSchema group(String code, String name, String description, RuleItemSchema... items) {
        return new RuleGroupSchema(code, name, description, List.of(items));
    }

    /**
     * 创建简化配置项（无范围限制和单位）。
     *
     * @param key             配置键名
     * @param label           UI 显示标签
     * @param valueType       值类型
     * @param consumerDomains 消费域列表
     * @param enabled         是否启用（null 视为 true）
     * @return 配置项 Schema
     */
    private RuleItemSchema item(
            String key,
            String label,
            ConfigValueType valueType,
            List<ConfigConsumerDomain> consumerDomains,
            Boolean enabled) {
        return item(key, label, valueType, consumerDomains, enabled, null, null, null, null);
    }

    /**
     * 创建完整配置项 Schema。
     *
     * @param key             配置键名
     * @param label           UI 显示标签
     * @param valueType       值类型
     * @param consumerDomains 消费域列表
     * @param enabled         是否启用（null 视为 true）
     * @param min             允许的最小值（可为 null）
     * @param max             允许的最大值（可为 null）
     * @param unit            单位标签（如 "天"、"%"、"件"，可为 null）
     * @param reservedNote    V2 预留说明（可为 null，表示当前参与实际计算）
     * @return 配置项 Schema
     */
    private RuleItemSchema item(
            String key,
            String label,
            ConfigValueType valueType,
            List<ConfigConsumerDomain> consumerDomains,
            Boolean enabled,
            Number min,
            Number max,
            String unit,
            String reservedNote) {
        boolean effectiveEnabled = enabled == null || enabled;
        return new RuleItemSchema(
                key,
                label,
                valueType,
                List.copyOf(consumerDomains),
                effectiveEnabled,
                min,
                max,
                unit,
                reservedNote,
                normalizeGroupFromKey(key));
    }

    /**
     * 从配置键名推断分组编码。
     * <p>
     * 取配置键中第一个点号之前的部分作为分组名（如 {@code sample.restrict_days} -> "sample"）。
     * 无点号时返回 "default"。
     * </p>
     *
     * @param key 配置键名
     * @return 推断的分组编码
     */
    private String normalizeGroupFromKey(String key) {
        if (key == null || !key.contains(".")) {
            return "default";
        }
        return key.substring(0, key.indexOf('.'));
    }

    /**
     * 规则分组 Schema 记录。
     * <p>
     * 表示规则中心的一个配置分组，包含分组编码、名称、描述和下属配置项列表。
     * 用于前端规则中心页面的分组展示。
     * </p>
     *
     * @param groupCode   分组编码（如 "sample"、"talent"、"commission"）
     * @param groupName   分组显示名称
     * @param description 分组描述
     * @param items       该分组下的配置项列表
     */
    public record RuleGroupSchema(
            String groupCode,
            String groupName,
            String description,
            List<RuleItemSchema> items) {
    }

    /**
     * 配置项 Schema 记录。
     * <p>
     * 描述单个配置项在规则中心 UI 中的展示元数据，
     * 包括标签、值类型、取值范围、单位、消费域等信息。
     * </p>
     *
     * @param key             配置键名
     * @param label           UI 显示标签
     * @param valueType       值类型
     * @param consumerDomains 消费此配置的业务域列表
     * @param enabled         是否启用
     * @param min             允许的最小值（可为 null）
     * @param max             允许的最大值（可为 null）
     * @param unit            单位标签（如 "天"、"%"）
     * @param reservedNote    V2 预留说明（非 null 时表示当前不参与实际计算）
     * @param legacyGroup     从配置键推断的原始分组名（用于 groupCode 映射）
     */
    public record RuleItemSchema(
            String key,
            String label,
            ConfigValueType valueType,
            List<ConfigConsumerDomain> consumerDomains,
            boolean enabled,
            Number min,
            Number max,
            String unit,
            String reservedNote,
            String legacyGroup) {

        /**
         * 获取配置项所属的规则分组编码。
         * <p>
         * 通过 switch 表达式将原始分组名映射到最终分组编码。
         * 特殊处理：talent 分组中含 "exclusive" 的键映射到 exclusive 分组；
         * merchant 映射到 exclusive；auth 映射到 security。
         * </p>
         *
         * @return 分组编码
         */
        public String groupCode() {
            return switch (legacyGroup) {
                case "sample" -> "sample";
                case "talent" -> key.contains("exclusive") ? "exclusive" : "talent";
                case "merchant" -> "exclusive";
                case "commission" -> "commission";
                case "promotion" -> "promotion";
                case "auth", "security" -> "security";
                default -> legacyGroup;
            };
        }
    }

    /**
     * 内部构建结果记录，封装分组映射和配置项映射。
     *
     * @param groups      分组编码到分组 Schema 的映射
     * @param itemsByKey  配置键到配置项 Schema 的映射
     */
    private record BuiltSchema(Map<String, RuleGroupSchema> groups, Map<String, RuleItemSchema> itemsByKey) {
    }

    /**
     * 校验提成比例的业务警告。
     * <p>
     * 检查招商提成比例和渠道提成比例的合计是否超过 100%（即 1.0）。
     * 超过时生成警告信息，由前端展示给运营人员。不影响配置保存（非阻断性校验）。
     * </p>
     *
     * @param values 配置键值对（包含提成比例的字符串值）
     * @return 警告信息列表，无警告时返回空列表
     */
    public List<String> validateCommissionWarning(Map<String, String> values) {
        List<String> warnings = new ArrayList<>();
        try {
            double recruiter = parseDecimal(values.get(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO));
            double channel = parseDecimal(values.get(SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO));
            if (recruiter + channel > 1D) {
                warnings.add("招商与渠道提成比例合计超过 100%，建议合计 ≤ 1");
            }
        } catch (Exception ignored) {
            // validation errors handled elsewhere
        }
        return warnings;
    }

    /**
     * 解析小数值字符串。
     * <p>null 或空白字符串返回 0.0。</p>
     *
     * @param raw 字符串值
     * @return 解析后的 double 值，null/空白时返回 0.0
     * @throws NumberFormatException 非法数字格式时抛出
     */
    private double parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0D;
        }
        return Double.parseDouble(raw.trim());
    }
}
