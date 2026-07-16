package com.colonel.saas.domain.order.policy;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.domain.shared.policy.DomainText;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 订单默认归因输入（DDD-ORDER-004）。
 */
public record OrderAttributionInput(
        String productId,
        String activityId,
        String pickSource,
        String pickExtra,
        String talentUid,
        UUID talentId,
        String colonelBuyinId,
        String secondColonelBuyinId,
        String secondActivityId,
        LocalDateTime businessTime) {

    public OrderAttributionInput(
            String productId,
            String activityId,
            String pickSource,
            String pickExtra,
            String talentUid,
            UUID talentId) {
        this(productId, activityId, pickSource, pickExtra, talentUid, talentId, null, null, null, null);
    }

    public static OrderAttributionInput from(ColonelsettlementOrder order, Map<String, Object> rawPayload) {
        String activityId = firstNonBlank(
                order == null ? null : order.getActivityId(),
                rawValue(rawPayload, "colonel_activity_id", "activity_id"));
        String productId = firstNonBlank(
                order == null ? null : order.getProductId(),
                rawValue(rawPayload, "product_id", "productId"));
        String pickSource = firstNonBlank(
                order == null ? null : order.getPickSource(),
                rawValue(rawPayload, "pick_source", "pickSource"));
        String pickExtra = rawValue(rawPayload, "pick_extra", "pickExtra");
        String talentUid = talentUid(rawPayload, order);
        UUID talentId = order == null ? null : order.getTalentId();
        String colonelBuyinId = firstNonBlank(
                order == null || order.getColonelBuyinId() == null ? null : order.getColonelBuyinId().toString(),
                rawValue(rawPayload, "colonel_buyin_id", "colonelBuyinId"));
        String secondColonelBuyinId = firstNonBlank(
                order == null || order.getSecondColonelBuyinId() == null ? null : order.getSecondColonelBuyinId().toString(),
                rawValue(rawPayload, "second_colonel_buyin_id", "secondColonelBuyinId"));
        String secondActivityId = firstNonBlank(
                order == null ? null : order.getSecondActivityId(),
                rawValue(rawPayload, "second_colonel_activity_id", "secondActivityId"));
        LocalDateTime businessTime = businessTime(order);
        return new OrderAttributionInput(
                productId,
                activityId,
                pickSource,
                pickExtra,
                talentUid,
                talentId,
                colonelBuyinId,
                secondColonelBuyinId,
                secondActivityId,
                businessTime);
    }

    private static LocalDateTime businessTime(ColonelsettlementOrder order) {
        if (order == null) {
            return null;
        }
        if (order.getPayTime() != null) {
            return order.getPayTime();
        }
        if (order.getOrderCreateTime() != null) {
            return order.getOrderCreateTime();
        }
        return order.getCreateTime();
    }

    private static String talentUid(Map<String, Object> rawPayload, ColonelsettlementOrder order) {
        String fromRaw = firstNonBlank(
                rawValue(rawPayload, "author_id", "authorId"),
                rawValue(rawPayload, "talent_uid", "talentUid"),
                rawValue(rawPayload, "promotion_talent_uid", "promotionTalentUid"));
        if (DomainText.hasText(fromRaw)) {
            return fromRaw.trim();
        }
        return order == null ? null : order.getTalentName();
    }

    private static String rawValue(Map<String, Object> rawPayload, String... keys) {
        if (rawPayload == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (!DomainText.hasText(key)) {
                continue;
            }
            Object value = rawPayload.get(key);
            if (value != null && DomainText.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (DomainText.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
