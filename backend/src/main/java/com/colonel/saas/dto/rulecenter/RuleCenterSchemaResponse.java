package com.colonel.saas.dto.rulecenter;

import com.colonel.saas.config.RuleCenterSchemaRegistry.RuleGroupSchema;
import com.colonel.saas.config.RuleCenterSchemaRegistry.RuleItemSchema;

import java.util.List;
import java.util.Map;

/**
 * 规则中心 Schema 响应 DTO。
 * <p>
 * 返回规则中心的完整配置结构定义，按规则分组组织，每个分组包含多个规则项。
 * 每个规则项定义了键名、标签、值类型、范围约束、单位和消费域等元数据。
 * 关联业务领域：规则中心（RuleCenter）。
 * </p>
 */
public record RuleCenterSchemaResponse(
        /** 规则分组列表 */
        List<RuleGroupView> groups) {

    /**
     * 规则分组视图。
     */
    public record RuleGroupView(
            /** 分组编码 */
            String groupCode,
            /** 分组名称 */
            String groupName,
            /** 分组描述 */
            String description,
            /** 分组内的规则项列表 */
            List<RuleItemView> items) {

        /**
         * 从规则分组 Schema 转换为视图对象。
         *
         * @param schema 规则分组 Schema
         * @return 对应的 {@link RuleGroupView} 实例
         */
        public static RuleGroupView from(RuleGroupSchema schema) {
            return new RuleGroupView(
                    schema.groupCode(),
                    schema.groupName(),
                    schema.description(),
                    schema.items().stream().map(RuleItemView::from).toList());
        }
    }

    /**
     * 规则项视图。
     */
    public record RuleItemView(
            /** 规则项编码（唯一标识） */
            String key,
            /** 规则项显示标签 */
            String label,
            /** 值类型（如 number、string、boolean） */
            String valueType,
            /** 最小值约束（数值类型时有效） */
            Number min,
            /** 最大值约束（数值类型时有效） */
            Number max,
            /** 值单位（如 percent、yuan） */
            String unit,
            /** 消费该规则项的业务域列表 */
            List<String> consumerDomains,
            /** 该规则项是否启用 */
            boolean enabled,
            /** 预留说明（仅管理员可见的备注） */
            String reservedNote) {

        /**
         * 从规则项 Schema 转换为视图对象。
         *
         * @param schema 规则项 Schema
         * @return 对应的 {@link RuleItemView} 实例
         */
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
