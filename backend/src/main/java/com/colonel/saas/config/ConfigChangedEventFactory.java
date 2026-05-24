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

@Component
public class ConfigChangedEventFactory {

    public static final String AGGREGATE_TYPE = "SYSTEM_CONFIG";

    private final RuleCenterSchemaRegistry ruleCenterSchemaRegistry;

    public ConfigChangedEventFactory(RuleCenterSchemaRegistry ruleCenterSchemaRegistry) {
        this.ruleCenterSchemaRegistry = ruleCenterSchemaRegistry;
    }

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
            RuleCenterSchemaRegistry.RuleItemSchema schema = ruleCenterSchemaRegistry.findItem(change.configKey()).orElse(null);
            String group = schema == null ? "default" : schema.groupCode();
            String valueType = schema == null
                    ? inferValueType(change.configKey())
                    : schema.valueType().name().toLowerCase();
            List<String> consumerDomains = schema == null
                    ? List.of()
                    : schema.consumerDomains().stream().map(ConfigConsumerDomain::code).toList();
            int configVersion = change.configVersion() <= 0 ? 1 : change.configVersion();
            items.add(new ConfigChangedItemPayload(
                    change.configKey(),
                    group,
                    change.oldValue(),
                    change.newValue(),
                    valueType,
                    configVersion,
                    consumerDomains));
            if (isCommissionKey(change.configKey())) {
                needManualRecalculate = true;
            }
        }
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

    private boolean isCommissionKey(String configKey) {
        return SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO.equals(configKey)
                || SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO.equals(configKey);
    }

    private String inferValueType(String configKey) {
        return ruleCenterSchemaRegistry.findDefinition(configKey)
                .map(ConfigDefinition::valueType)
                .map(ConfigValueType::name)
                .map(String::toLowerCase)
                .orElse("string");
    }

    public record ConfigChangeContext(
            String configKey,
            String oldValue,
            String newValue,
            int configVersion) {

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
