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

@Component
public class RuleCenterSchemaRegistry {

    private final ConfigDefinitionRegistry configDefinitionRegistry;
    private final Map<String, RuleGroupSchema> groups;
    private final Map<String, RuleItemSchema> itemsByKey;

    public RuleCenterSchemaRegistry(ConfigDefinitionRegistry configDefinitionRegistry) {
        this.configDefinitionRegistry = configDefinitionRegistry;
        var built = buildSchema();
        this.groups = built.groups();
        this.itemsByKey = built.itemsByKey();
    }

    public List<RuleGroupSchema> groups() {
        return List.copyOf(groups.values());
    }

    public Optional<RuleItemSchema> findItem(String key) {
        return Optional.ofNullable(itemsByKey.get(normalize(key)));
    }

    public Optional<ConfigDefinition> findDefinition(String key) {
        return configDefinitionRegistry.find(key);
    }

    public Optional<RuleGroupSchema> findGroup(String groupCode) {
        return Optional.ofNullable(groups.get(normalize(groupCode)));
    }

    public List<RuleItemSchema> itemsInGroup(String groupCode) {
        RuleGroupSchema group = groups.get(normalize(groupCode));
        return group == null ? List.of() : group.items();
    }

    public Set<String> keysInGroup(String groupCode) {
        return itemsInGroup(groupCode).stream().map(RuleItemSchema::key).collect(java.util.stream.Collectors.toSet());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

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
                "达人认领保护期等业务规则",
                item(SystemConfigKeys.TALENT_PROTECTION_DAYS, "达人保护期", ConfigValueType.INTEGER,
                        List.of(ConfigConsumerDomain.TALENT), true, 1, 365, "天", null)));

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

    private void registerGroup(
            Map<String, RuleGroupSchema> groupMap,
            Map<String, RuleItemSchema> itemMap,
            RuleGroupSchema group) {
        groupMap.put(normalize(group.groupCode()), group);
        for (RuleItemSchema item : group.items()) {
            itemMap.put(normalize(item.key()), item);
        }
    }

    private RuleGroupSchema group(String code, String name, String description, RuleItemSchema... items) {
        return new RuleGroupSchema(code, name, description, List.of(items));
    }

    private RuleItemSchema item(
            String key,
            String label,
            ConfigValueType valueType,
            List<ConfigConsumerDomain> consumerDomains,
            Boolean enabled) {
        return item(key, label, valueType, consumerDomains, enabled, null, null, null, null);
    }

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

    private String normalizeGroupFromKey(String key) {
        if (key == null || !key.contains(".")) {
            return "default";
        }
        return key.substring(0, key.indexOf('.'));
    }

    public record RuleGroupSchema(
            String groupCode,
            String groupName,
            String description,
            List<RuleItemSchema> items) {
    }

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

    private record BuiltSchema(Map<String, RuleGroupSchema> groups, Map<String, RuleItemSchema> itemsByKey) {
    }

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

    private double parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0D;
        }
        return Double.parseDouble(raw.trim());
    }
}
