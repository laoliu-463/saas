package com.colonel.saas.config;

import com.colonel.saas.config.ConfigDefinitionRegistry.ConfigDefinition;
import com.colonel.saas.config.ConfigDefinitionRegistry.ConfigValueType;
import com.colonel.saas.domain.event.ConfigChangedEventPayload;
import com.colonel.saas.domain.event.ConfigChangedImpactPayload;
import com.colonel.saas.domain.event.ConfigChangedItemPayload;
import com.colonel.saas.entity.SystemConfig;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 系统配置变更事件工厂。
 * <p>
 * 负责将系统配置的变更操作转换为标准化的事件载荷（{@link ConfigChangedEventPayload}），
 * 供事件溯源（Event Sourcing）和下游消费者使用。每个变更事件包含：
 * <ul>
 *   <li>变更明细（哪些配置项发生了变更、新旧值是什么）</li>
 *   <li>影响范围标记（是否需要人工重算、是否影响业绩域）</li>
 *   <li>操作者信息（谁发起的变更、变更原因）</li>
 * </ul>
 *
 * <p>关键业务规则：</p>
 * <ul>
 *   <li>提成相关配置（{@code commission.business_default_ratio}、{@code commission.channel_default_ratio}）
 *       变更时，会自动标记 {@code needManualRecalculate=true}，提醒运营人员触发手动重算</li>
 *   <li>配置值类型（string/integer/decimal/boolean/json）优先从规则中心 Schema 中获取，
 *       未注册的配置项回退到 {@link ConfigDefinitionRegistry} 推断</li>
 *   <li>版本号低于 1 时自动修正为 1，避免无效版本传播</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link RuleCenterSchemaRegistry} —— 查询配置项的 Schema 元数据（分组、值类型、消费域）</li>
 *   <li>{@link ConfigDefinitionRegistry} —— 作为值类型推断的后备来源</li>
 *   <li>{@link ConfigConsumerDomain} —— 消费域枚举，标识变更影响的业务模块</li>
 *   <li>{@link SystemConfigKeys} —— 配置键名常量，用于判断是否为提成相关键</li>
 * </ul>
 *
 * @see ConfigChangedEventPayload
 * @see RuleCenterSchemaRegistry
 */
@Component
public class ConfigChangedEventFactory {

    /** 事件聚合类型标识，用于事件溯源系统 */
    public static final String AGGREGATE_TYPE = "SYSTEM_CONFIG";

    /** 规则中心 Schema 注册器，用于查询配置项的元数据 */
    private final RuleCenterSchemaRegistry ruleCenterSchemaRegistry;

    public ConfigChangedEventFactory(RuleCenterSchemaRegistry ruleCenterSchemaRegistry) {
        this.ruleCenterSchemaRegistry = ruleCenterSchemaRegistry;
    }

    /**
     * 创建配置变更事件载荷。
     * <p>
     * 遍历所有变更项，从规则中心 Schema 中获取分组、值类型、消费域等元数据，
     * 并检测是否涉及提成相关配置。若涉及提成配置，自动标记需要手动重算。
     * </p>
     *
     * @param eventId      事件唯一标识
     * @param operatorId   操作者 ID
     * @param operatorName 操作者姓名
     * @param changeReason 变更原因说明
     * @param source       变更来源（如 "admin-ui"、"api" 等）
     * @param changes      配置变更上下文列表
     * @return 构建完成的配置变更事件载荷
     */
    public ConfigChangedEventPayload create(
            UUID eventId,
            UUID operatorId,
            String operatorName,
            String changeReason,
            String source,
            List<ConfigChangeContext> changes) {
        List<ConfigChangedItemPayload> items = new ArrayList<>();
        boolean needManualRecalculate = false;
        for (ConfigChangeContext change : changes) {
            // 从规则中心获取配置项 Schema 元数据
            RuleCenterSchemaRegistry.RuleItemSchema schema = ruleCenterSchemaRegistry.findItem(change.configKey()).orElse(null);
            // 分组信息：优先使用 Schema 定义的分组，未注册时使用 "default"
            String group = schema == null ? "default" : schema.groupCode();
            // 值类型：优先使用 Schema 定义的类型，未注册时从 ConfigDefinitionRegistry 推断
            String valueType = schema == null
                    ? inferValueType(change.configKey())
                    : schema.valueType().name().toLowerCase();
            // 消费域列表：标识变更影响的业务模块
            List<String> consumerDomains = schema == null
                    ? List.of()
                    : schema.consumerDomains().stream().map(ConfigConsumerDomain::code).toList();
            // 版本号修正：防止无效版本传播到下游
            int configVersion = change.configVersion() <= 0 ? 1 : change.configVersion();
            items.add(new ConfigChangedItemPayload(
                    change.configKey(),
                    group,
                    change.oldValue(),
                    change.newValue(),
                    valueType,
                    configVersion,
                    consumerDomains));
            // 检测是否为提成相关配置变更
            if (isCommissionKey(change.configKey())) {
                needManualRecalculate = true;
            }
        }
        // 构建影响范围标记
        ConfigChangedImpactPayload impact = new ConfigChangedImpactPayload(
                true,
                needManualRecalculate,
                needManualRecalculate);
        return new ConfigChangedEventPayload(
                eventId,
                ConfigChangedEventPayload.EVENT_TYPE,
                1,
                operatorId,
                operatorName,
                LocalDateTime.now(),
                changeReason,
                source,
                items,
                impact);
    }

    /**
     * 判断配置键是否为提成相关键。
     * <p>提成比例变更需要标记为"需手动重算"，避免自动应用影响历史业绩。</p>
     *
     * @param configKey 配置键名
     * @return 是否为提成相关配置键
     */
    private boolean isCommissionKey(String configKey) {
        return SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO.equals(configKey)
                || SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO.equals(configKey);
    }

    /**
     * 从 ConfigDefinitionRegistry 推断配置值类型。
     * <p>当配置项未在规则中心 Schema 中注册时，作为后备的类型推断方式。</p>
     *
     * @param configKey 配置键名
     * @return 值类型名称的小写形式（如 "string"、"integer"），默认 "string"
     */
    private String inferValueType(String configKey) {
        return ruleCenterSchemaRegistry.findDefinition(configKey)
                .map(ConfigDefinition::valueType)
                .map(ConfigValueType::name)
                .map(String::toLowerCase)
                .orElse("string");
    }

    /**
     * 配置变更上下文记录，封装单个配置项的变更信息。
     * <p>
     * 使用 Java Record 表示不可变的值对象，包含配置键、旧值、新值和版本号。
     * </p>
     *
     * @param configKey      配置键名
     * @param oldValue       变更前的值（新建时为 null）
     * @param newValue       变更后的值
     * @param configVersion  配置版本号
     */
    public record ConfigChangeContext(
            String configKey,
            String oldValue,
            String newValue,
            int configVersion) {

        /**
         * 从变更前后的 SystemConfig 实体构建变更上下文。
         *
         * @param before 变更前的配置实体（新建时为 null）
         * @param after  变更后的配置实体（不能为 null）
         * @return 配置变更上下文记录
         */
        public static ConfigChangeContext from(SystemConfig before, SystemConfig after) {
            int version = after.getConfigVersion() == null ? 1 : after.getConfigVersion();
            return new ConfigChangeContext(
                    after.getConfigKey(),
                    before == null ? null : before.getConfigValue(),
                    after.getConfigValue(),
                    version);
        }
    }
}
