package com.colonel.saas.domain.order.policy;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.domain.shared.policy.DomainText;

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
        String colonelBuyinId,
        String secondColonelBuyinId,
        String secondActivityId,
        String talentUid,
        UUID talentId) {

    /** Compatibility constructor for policy tests and callers that only have pick-source facts. */
    public OrderAttributionInput(
            String productId,
            String activityId,
            String pickSource,
            String pickExtra,
            String talentUid,
            UUID talentId) {
        this(productId, activityId, pickSource, pickExtra, null, null, null, talentUid, talentId);
    }

    public static OrderAttributionInput from(ColonelsettlementOrder order, Map<String, Object> rawPayload) {
        String activityId = firstNonBlank(
                rawValue(rawPayload, "colonel_activity_id", "activity_id"),
                order == null ? null : order.getActivityId());
        String productId = order == null ? null : order.getProductId();
        String pickSource = order == null ? null : order.getPickSource();
        String pickExtra = rawValue(rawPayload, "pick_extra", "pickExtra");
        String colonelBuyinId = firstNonBlank(
                rawValue(rawPayload, "colonel_buyin_id", "colonelBuyinId"),
                order == null || order.getColonelBuyinId() == null
                        ? null : String.valueOf(order.getColonelBuyinId()));
        String secondColonelBuyinId = firstNonBlank(
                rawValue(rawPayload, "second_colonel_buyin_id", "secondColonelBuyinId"),
                order == null || order.getSecondColonelBuyinId() == null
                        ? null : String.valueOf(order.getSecondColonelBuyinId()));
        String secondActivityId = firstNonBlank(
                rawValue(rawPayload, "second_colonel_activity_id", "secondColonelActivityId"),
                order == null ? null : order.getSecondActivityId());
        String talentUid = talentUid(rawPayload, order);
        UUID talentId = order == null ? null : order.getTalentId();
        return new OrderAttributionInput(
                productId,
                activityId,
                pickSource,
                pickExtra,
                colonelBuyinId,
                secondColonelBuyinId,
                secondActivityId,
                talentUid,
                talentId);
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

    public boolean hasNativeColonelIdentity() {
        return DomainText.hasText(colonelBuyinId) || DomainText.hasText(secondColonelBuyinId);
    }
}
