package com.colonel.saas.domain.event;

/**
 * 配置变更影响评估载荷，描述一次配置变更对系统的影响范围。
 *
 * <p>由配置变更事件产生方在写入事件时评估并填写，
 * 消费者可据此决定处理策略（如是否需要全量刷新缓存、是否需要手动重算等）。</p>
 */
public record ConfigChangedImpactPayload(
        /** 是否需要刷新缓存。true 表示消费者应立即失效相关本地缓存。 */
        boolean needCacheRefresh,
        /** 是否需要手动重算。true 表示变更涉及历史数据，需管理员手动触发重算任务。 */
        boolean needManualRecalculate,
        /** 是否仅影响新数据。true 表示变更只对新产生的数据生效，历史数据不受影响。 */
        boolean affectNewDataOnly) {
}
