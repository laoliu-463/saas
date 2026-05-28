package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品负责人变更事件。
 *
 * <p>当商品的运营负责人（assignee）发生变更时触发，如转派、重新分配、取消负责等。
 * 该事件通过 {@link ProductDomainEventPublisher#publishProductOwnerChanged} 发布到 Outbox。</p>
 */
public record ProductOwnerChangedEvent(
        /** 事件唯一标识。 */
        UUID eventId,
        /** 活动 ID（商品所属活动）。 */
        String activityId,
        /** 商品 ID（聚合根 ID）。 */
        String productId,
        /** 原负责人 ID（可为 null，表示之前无负责人）。 */
        UUID oldAssigneeId,
        /** 新负责人 ID（可为 null，表示取消负责人）。 */
        UUID newAssigneeId,
        /** 操作人 ID（发起变更的管理员或系统）。 */
        UUID operatorId,
        /** 变更发生时间。 */
        LocalDateTime occurredAt,
        /** 链路追踪 ID（从 MDC 提取，可为 null）。 */
        String traceId) {
}
