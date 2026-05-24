package com.colonel.saas.dto.rulecenter;

import com.colonel.saas.config.RuleCenterSchemaRegistry.RuleGroupSchema;
import com.colonel.saas.config.RuleCenterSchemaRegistry.RuleItemSchema;

import java.util.List;
import java.util.Map;

public record RuleCenterSchemaResponse(List<RuleGroupView> groups) {

    public record RuleGroupView(
            String groupCode,
            String groupName,
            String description,
            List<RuleItemView> items) {

        public static RuleGroupView from(RuleGroupSchema schema) {
            return new RuleGroupView(
                    schema.groupCode(),
                    schema.groupName(),
                    schema.description(),
                    schema.items().stream().map(RuleItemView::from).toList());
        }
    }

    public record RuleItemView(
            String key,
            String label,
            String valueType,
            Number min,
            Number max,
            String unit,
            List<String> consumerDomains,
            boolean enabled,
            String reservedNote) {

        public static RuleItemView from(RuleItemSchema schema) {
            return new RuleItemView(
                    schema.key(),
                    schema.label(),
                    schema.valueType().name().toLowerCase(),
                    schema.min(),
                    schema.max(),
                    schema.unit(),
                    schema.consumerDomains().stream().map(domain -> domain.code()).toList(),
                    schema.enabled(),
                    schema.reservedNote());
        }
    }
}
