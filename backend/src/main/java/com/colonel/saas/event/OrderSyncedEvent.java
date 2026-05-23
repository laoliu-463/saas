package com.colonel.saas.event;

import java.util.Map;
import java.util.UUID;

/**
 * 订单同步落库后发布（O-08），供看板汇总等订阅方消费。
 * T-03: 新增 talentUid、extraData 字段用于达人保护期重置。
 */
public record OrderSyncedEvent(
        String orderId,
        UUID orderRowId,
        boolean newlyInserted,
        String attributionStatus,
        long orderAmount,
        long payAmount,
        long settleAmount,
        long estimateServiceFee,
        long effectiveServiceFee,
        long estimateTechServiceFee,
        long effectiveTechServiceFee,
        long settleColonelCommission,
        long settleColonelTechServiceFee,
        long settleSecondColonelCommission,
        Integer orderStatus,
        String talentUid,
        Map<String, Object> extraData) {
}
