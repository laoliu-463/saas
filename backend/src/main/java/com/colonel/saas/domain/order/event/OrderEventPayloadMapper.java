package com.colonel.saas.domain.order.event;

import com.colonel.saas.domain.order.policy.OrderDefaultAttributionResult;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.event.OrderSyncedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 订单事件载荷映射（DDD-ORDER-005）。
 */
@Component
public class OrderEventPayloadMapper {

    public OrderSyncedEvent toOrderSyncedEvent(ColonelsettlementOrder order, boolean newlyInserted) {
        if (order == null) {
            return null;
        }
        return new OrderSyncedEvent(
                order.getOrderId(),
                order.getId(),
                newlyInserted,
                order.getAttributionStatus(),
                order.getOrderAmount() == null ? 0L : order.getOrderAmount(),
                order.getOrderAmount() == null ? 0L : order.getOrderAmount(),
                order.getSettleAmount() == null ? 0L : order.getSettleAmount(),
                order.getEstimateServiceFee() == null ? 0L : order.getEstimateServiceFee(),
                order.getEffectiveServiceFee() == null ? 0L : order.getEffectiveServiceFee(),
                order.getEstimateServiceFeeExpense() == null ? 0L : order.getEstimateServiceFeeExpense(),
                order.getEffectiveServiceFeeExpense() == null ? 0L : order.getEffectiveServiceFeeExpense(),
                order.getEstimateTechServiceFee() == null ? 0L : order.getEstimateTechServiceFee(),
                order.getEffectiveTechServiceFee() == null ? 0L : order.getEffectiveTechServiceFee(),
                order.getSettleColonelCommission() == null ? 0L : order.getSettleColonelCommission(),
                order.getSettleColonelTechServiceFee() == null ? 0L : order.getSettleColonelTechServiceFee(),
                order.getSettleSecondColonelCommission() == null ? 0L : order.getSettleSecondColonelCommission(),
                order.getOrderStatus(),
                resolveOrderCreateTime(order),
                resolveTalentUid(order.getExtraData()),
                enrichExtraDataWithDualAttributionStatus(order),
                order.getProductId(),
                order.getActivityId(),
                order.getShopId() == null ? null : String.valueOf(order.getShopId()),
                order.getTalentId(),
                order.getChannelUserId(),
                order.getColonelUserId(),
                resolveRecruiterAttribution(order),
                order.getPickSource(),
                order.getPayTime(),
                order.getSettleTime(),
                !newlyInserted,
                LocalDateTime.now());
    }

    public OrderStatusChangedEvent toOrderStatusChangedEvent(
            ColonelsettlementOrder order,
            Integer previousStatus,
            boolean newlyInserted) {
        if (order == null) {
            return null;
        }
        return new OrderStatusChangedEvent(
                order.getOrderId(),
                order.getId(),
                previousStatus,
                order.getOrderStatus(),
                newlyInserted,
                order.getAttributionStatus(),
                java.time.LocalDateTime.now());
    }

    public OrderRefundFactSyncedEvent toOrderRefundFactSyncedEvent(
            ColonelsettlementOrder order,
            Integer previousStatus) {
        if (order == null) {
            return null;
        }
        return new OrderRefundFactSyncedEvent(
                order.getOrderId(),
                order.getId(),
                resolveString(order.getExtraData(), List.of("refund_id", "refundId", "after_sale_id", "afterSaleId")),
                resolveLong(order.getExtraData(), List.of("refund_amount", "refundAmount", "refund_fee", "refundFee")),
                previousStatus,
                order.getOrderStatus(),
                order.getFlowPoint(),
                order.getExtraData(),
                LocalDateTime.now());
    }

    private LocalDateTime resolveOrderCreateTime(ColonelsettlementOrder order) {
        if (order.getOrderCreateTime() != null) {
            return order.getOrderCreateTime();
        }
        return order.getCreateTime();
    }

    private String resolveRecruiterAttribution(ColonelsettlementOrder order) {
        return order.getColonelUserId() == null ? null : "DEFAULT";
    }

    private String resolveTalentUid(Map<String, Object> extraData) {
        if (extraData == null || extraData.isEmpty()) {
            return null;
        }
        for (String key : List.of("author_id", "talent_uid", "talentUid", "authorId", "talent_id")) {
            Object value = extraData.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }

    /**
     * 复制订单 extraData 并注入双维度归属状态。
     * <p>为什么不在 record 字段里加：{@code OrderSyncedEvent} 已是 36 参数 record，
     * 改 record 会触发 Outbox 序列化 / 反序列化 / 全部调用方一连串升级。当前切片先走 extraData
     * 通道，下一切片再决定是否升级为 record 字段。</p>
     * <p>不可变保护：调用方传入的 {@code Map.of(...)} 不可变；这里复制为 {@code LinkedHashMap}，
     * 不修改调用方引用。</p>
     */
    private Map<String, Object> enrichExtraDataWithDualAttributionStatus(ColonelsettlementOrder order) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        Map<String, Object> source = order.getExtraData();
        if (source != null) {
            enriched.putAll(source);
        }
        enriched.put(
                "channel_attribution_status",
                resolveChannelAttributionStatus(order));
        enriched.put(
                "recruiter_attribution_status",
                resolveRecruiterAttributionStatus(order));
        return enriched;
    }

    private String resolveChannelAttributionStatus(ColonelsettlementOrder order) {
        if (order != null
                && StringUtils.hasText(order.getChannelAttributionStatus())) {
            return order.getChannelAttributionStatus();
        }
        if (order != null && order.getChannelUserId() != null) {
            return OrderDefaultAttributionResult.CHANNEL_ATTRIBUTED;
        }
        return OrderDefaultAttributionResult.CHANNEL_UNATTRIBUTED;
    }

    private String resolveRecruiterAttributionStatus(ColonelsettlementOrder order) {
        if (order != null
                && StringUtils.hasText(order.getRecruiterAttributionStatus())) {
            return order.getRecruiterAttributionStatus();
        }
        UUID recruiterId = order == null ? null : order.getColonelUserId();
        return recruiterId == null
                ? OrderDefaultAttributionResult.RECRUITER_UNATTRIBUTED
                : OrderDefaultAttributionResult.RECRUITER_ATTRIBUTED;
    }

    private String resolveString(Map<String, Object> extraData, List<String> keys) {
        if (extraData == null || extraData.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = extraData.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private Long resolveLong(Map<String, Object> extraData, List<String> keys) {
        if (extraData == null || extraData.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = extraData.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null && StringUtils.hasText(value.toString())) {
                try {
                    return Long.parseLong(value.toString().trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
