package com.colonel.saas.domain.event;

import java.util.List;

/**
 * 配置变更项载荷，描述单个配置项的变更详情。
 *
 * <p>一次配置变更事件（{@link ConfigChangedEventPayload}）可包含多个变更项，
 * 每个变更项记录了配置键、旧值、新值以及该配置项影响的消费者域列表。</p>
 */
public record ConfigChangedItemPayload(
        /** 配置键名，如 {@code COMMISSION_BUSINESS_DEFAULT_RATIO}，对应 {@code SystemConfigKeys} 常量。 */
        String configKey,
        /** 配置分组标识，用于逻辑分类（如 {@code commission}、{@code sample}、{@code user}）。 */
        String group,
        /** 变更前的配置值（JSON 字符串形式）。 */
        String oldValue,
        /** 变更后的配置值（JSON 字符串形式）。 */
        String newValue,
        /** 值类型标识，如 {@code integer}、{@code decimal}、{@code boolean}、{@code json}。 */
        String valueType,
        /** 配置版本号，每次变更递增，用于乐观锁和版本比对。 */
        int configVersion,
        /**
         * 该配置项影响的消费者域列表。
         * 消费者可据此做快速过滤，而非硬编码关心的 configKey。
         */
        List<String> consumerDomains) {
}
