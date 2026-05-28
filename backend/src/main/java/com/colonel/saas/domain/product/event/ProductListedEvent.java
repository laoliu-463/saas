package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品上架事件（加入商品库并参与展示竞争）。
 *
 * <p>当商品通过选品规则或手动操作加入商品库时触发。
 * 该事件通过 {@link ProductDomainEventPublisher#publishProductListed} 发布到 Outbox。</p>
 */
public record ProductListedEvent(
        /** 事件唯一标识。 */
        UUID eventId,
        /** 活动 ID（商品所属活动）。 */
        String activityId,
        /** 商品 ID（聚合根 ID）。 */
        String productId,
        /** 操作状态记录 ID，用于幂等去重和展示规则追踪。 */
        UUID operationStateId,
        /** 操作人 ID（手动上架时为操作员，自动上架时为系统）。 */
        UUID operatorId,
        /** 上架发生时间。 */
        LocalDateTime occurredAt,
        /** 链路追踪 ID（从 MDC 提取，可为 null）。 */
        String traceId) {
}
