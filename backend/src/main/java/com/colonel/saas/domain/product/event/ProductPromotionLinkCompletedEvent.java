package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品转链完成事件。
 *
 * <p>当商品转链成功且本地 {@code promotion_link} / {@code pick_source_mapping}
 * 已落库后触发，用于给订单归因、业绩和审计链路提供可追踪事实。</p>
 */
public record ProductPromotionLinkCompletedEvent(
        UUID eventId,
        String activityId,
        String productId,
        UUID promotionLinkId,
        UUID mappingId,
        UUID operatorId,
        String talentId,
        String pickSource,
        String pickExtra,
        String promoteLink,
        String shortLink,
        String scene,
        LocalDateTime occurredAt,
        String traceId) {
}
