package com.colonel.saas.domain.order.event;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.event.OrderSyncedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
                order.getExtraData(),
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

    private LocalDateTime resolveOrderCreateTime(ColonelsettlementOrder order) {
        if (order.getOrderCreateTime() != null) {
            return order.getOrderCreateTime();
        }
        return order.getCreateTime();
    }

    private String resolveRecruiterAttribution(ColonelsettlementOrder order) {
        if (order.getColonelUserId() != null) {
            return "DEFAULT";
        }
        if (StringUtils.hasText(order.getAttributionStatus())) {
            return order.getAttributionStatus();
        }
        return null;
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
}
