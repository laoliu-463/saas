package com.colonel.saas.service;

import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 从抖店订单 raw 载荷解析双轨金额（订单域 V1.6 §2.4.2）。金额单位：分。
 */
public final class OrderDualTrackAmountResolver {

    private OrderDualTrackAmountResolver() {
    }

    public record DualTrackAmounts(
            long payAmount,
            long settleAmount,
            long estimateServiceFee,
            long effectiveServiceFee,
            long estimateTechServiceFee,
            long effectiveTechServiceFee) {
    }

    public static DualTrackAmounts resolve(
            Map<String, Object> rawPayload,
            Long fallbackPayAmount,
            Long fallbackServiceFee) {
        long payAmount = firstPositive(
                asLong(pick(rawPayload, "pay_goods_amount", "payGoodsAmount", "order_amount", "orderAmount",
                        "total_pay_amount", "totalPayAmount", "pay_amount", "payAmount")),
                fallbackPayAmount);
        long settleAmount = firstPositive(
                asLong(pick(rawPayload, "settled_goods_amount", "settledGoodsAmount", "settle_goods_amount",
                        "settleGoodsAmount", "settle_amount", "settleAmount", "actual_amount", "actualAmount")),
                payAmount);

        long estimateServiceFee = firstNonNegative(asLong(pickNested(rawPayload,
                "estimated_commission", "estimatedCommission", "estimated_service_fee", "estimatedServiceFee",
                "estimate_institution_commission", "estimateInstitutionCommission")));
        long effectiveServiceFee = firstNonNegative(asLong(pickNested(rawPayload,
                "settle_colonel_commission", "settleColonelCommission", "commission", "real_commission",
                "realCommission", "settled_commission", "settledCommission", "institution_commission",
                "institutionCommission", "colonel_commission", "colonelCommission", "service_fee", "serviceFee")));

        if (estimateServiceFee <= 0 && effectiveServiceFee > 0) {
            estimateServiceFee = effectiveServiceFee;
        }
        if (estimateServiceFee <= 0 && fallbackServiceFee != null && fallbackServiceFee > 0) {
            estimateServiceFee = fallbackServiceFee;
        }
        if (effectiveServiceFee <= 0 && estimateServiceFee > 0) {
            // 待结算单：结算轨常为 0，保留预估
        } else if (effectiveServiceFee <= 0 && fallbackServiceFee != null && fallbackServiceFee > 0) {
            effectiveServiceFee = fallbackServiceFee;
        }

        long estimateTechServiceFee = firstNonNegative(asLong(pickNested(rawPayload,
                "estimated_tech_service_fee", "estimatedTechServiceFee", "estimate_platform_service_fee",
                "estimatePlatformServiceFee", "settle_colonel_tech_service_fee", "settleColonelTechServiceFee")));
        long effectiveTechServiceFee = firstNonNegative(asLong(pickNested(rawPayload,
                "tech_service_fee", "techServiceFee", "settled_tech_service_fee", "settledTechServiceFee",
                "platform_service_fee", "platformServiceFee", "settle_colonel_tech_service_fee",
                "settleColonelTechServiceFee")));
        if (estimateTechServiceFee <= 0) {
            estimateTechServiceFee = effectiveTechServiceFee;
        }

        return new DualTrackAmounts(
                payAmount,
                settleAmount,
                estimateServiceFee,
                effectiveServiceFee,
                estimateTechServiceFee,
                effectiveTechServiceFee);
    }

    /**
     * 再次同步时保留已有预估轨快照（§2.4.2：更新 effective_*，保留 estimate_*）。
     */
    public static void mergeEstimateSnapshot(
            com.colonel.saas.entity.ColonelsettlementOrder existing,
            com.colonel.saas.entity.ColonelsettlementOrder incoming) {
        if (existing == null || incoming == null) {
            return;
        }
        if (isEmpty(incoming.getEstimateServiceFee()) && hasValue(existing.getEstimateServiceFee())) {
            incoming.setEstimateServiceFee(existing.getEstimateServiceFee());
        }
        if (isEmpty(incoming.getEstimateTechServiceFee()) && hasValue(existing.getEstimateTechServiceFee())) {
            incoming.setEstimateTechServiceFee(existing.getEstimateTechServiceFee());
        }
        if (isEmpty(incoming.getOrderAmount()) && hasValue(existing.getOrderAmount())) {
            incoming.setOrderAmount(existing.getOrderAmount());
        }
    }

    public static void applyToOrder(
            com.colonel.saas.entity.ColonelsettlementOrder order,
            DualTrackAmounts amounts) {
        if (order == null || amounts == null) {
            return;
        }
        order.setOrderAmount(amounts.payAmount());
        order.setActualAmount(amounts.settleAmount());
        order.setSettleAmount(amounts.settleAmount());
        order.setEstimateServiceFee(amounts.estimateServiceFee());
        order.setEffectiveServiceFee(amounts.effectiveServiceFee());
        order.setEstimateTechServiceFee(amounts.estimateTechServiceFee());
        order.setEffectiveTechServiceFee(amounts.effectiveTechServiceFee());
        // 兼容旧字段：结算轨优先
        order.setSettleColonelCommission(amounts.effectiveServiceFee() > 0
                ? amounts.effectiveServiceFee()
                : amounts.estimateServiceFee());
        order.setSettleColonelTechServiceFee(amounts.effectiveTechServiceFee() > 0
                ? amounts.effectiveTechServiceFee()
                : amounts.estimateTechServiceFee());
    }

    private static boolean isEmpty(Long value) {
        return value == null || value <= 0;
    }

    private static boolean hasValue(Long value) {
        return value != null && value > 0;
    }

    private static long firstPositive(Long primary, Long fallback) {
        if (primary != null && primary > 0) {
            return primary;
        }
        return fallback == null ? 0L : Math.max(fallback, 0L);
    }

    private static long firstNonNegative(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    private static Object pick(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object pickNested(Map<String, Object> rawPayload, String... keys) {
        Object direct = pick(rawPayload, keys);
        if (direct != null) {
            return direct;
        }
        Object nested = pick(rawPayload, "colonel_order_info", "colonelOrderInfo");
        if (nested instanceof Map<?, ?> map) {
            return pick((Map<String, Object>) map, keys);
        }
        return null;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
