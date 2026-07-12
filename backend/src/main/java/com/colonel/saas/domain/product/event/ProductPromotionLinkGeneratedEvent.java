package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品转链完成事件。
 *
 * <p>当推广链接生成成功并落本地推广链接 / pick_source 映射后触发。</p>
 */
public record ProductPromotionLinkGeneratedEvent(
        UUID eventId,
        String activityId,
        String productId,
        String talentId,
        UUID channelUserId,
        UUID deptId,
        UUID mappingId,
        String pickSource,
        String promotionLink,
        String shortLink,
        String scene,
        Integer promotionScene,
        String idempotencyKey,
        LocalDateTime occurredAt,
        String traceId) {
}
