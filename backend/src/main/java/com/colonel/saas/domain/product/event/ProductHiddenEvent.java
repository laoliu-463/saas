package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品从商品库展示中隐藏事件。
 *
 * <p>当商品因规则淘汰、活动结束、手动下架等原因从展示列表中移除时触发。
 * 该事件通过 {@link ProductDomainEventPublisher#publishProductHidden} 发布到 Outbox。</p>
 */
public record ProductHiddenEvent(
        /** 事件唯一标识。 */
        UUID eventId,
        /** 活动 ID（商品所属活动）。 */
        String activityId,
        /** 商品 ID（聚合根 ID）。 */
        String productId,
        /** 操作状态记录 ID，用于幂等去重和展示规则追踪。 */
        UUID operationStateId,
        /** 下架原因（如规则淘汰、活动结束、手动下架等）。 */
        String reason,
        /** 下架发生时间。 */
        LocalDateTime occurredAt,
        /** 链路追踪 ID（从 MDC 提取，可为 null）。 */
        String traceId) {
}
