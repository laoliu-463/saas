package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy;
import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy.MappedAmounts;
import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy.Track;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.OrderDualTrackAmountResolver;
import com.colonel.saas.service.OrderDualTrackAmountResolver.DualTrackAmounts;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单金额映射路由（DDD-ORDER-002）：在 legacy {@link OrderDualTrackAmountResolver}
 * 与 {@link OrderAmountMapperPolicy} 之间按安全开关委派，默认走 legacy，行为 1:1。
 */
@Service
public class OrderAmountMappingRouter {

    public enum SyncSource {
        INSTITUTE,
        INSTITUTE_SETTLEMENT,
        SETTLEMENT
    }

    private final DddRefactorProperties dddRefactorProperties;

    public OrderAmountMappingRouter(DddRefactorProperties dddRefactorProperties) {
        this.dddRefactorProperties = dddRefactorProperties;
    }

    public boolean isPolicyEnabled() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getOrderAmountPolicy().isEnabled();
    }

    public DualTrackAmounts resolveAmounts(
            SyncSource source,
            Map<String, Object> rawPayload,
            Long fallbackPayAmount,
            Long fallbackServiceFee) {
        if (!isPolicyEnabled()) {
            return switch (source) {
                case INSTITUTE -> OrderDualTrackAmountResolver.resolve(
                        rawPayload, fallbackPayAmount, fallbackServiceFee);
                case INSTITUTE_SETTLEMENT -> OrderDualTrackAmountResolver.resolveInstituteSettlement(rawPayload);
                case SETTLEMENT -> OrderDualTrackAmountResolver.resolveStrictSettlement(
                        rawPayload, fallbackPayAmount, fallbackServiceFee);
            };
        }
        return switch (source) {
            case INSTITUTE -> toDualTrack(
                    mapWithFallback(rawPayload, fallbackPayAmount, fallbackServiceFee, Track.INSTITUTE),
                    OrderDualTrackAmountResolver.resolve(rawPayload, fallbackPayAmount, fallbackServiceFee));
            case INSTITUTE_SETTLEMENT -> OrderDualTrackAmountResolver.resolveInstituteSettlement(rawPayload);
            case SETTLEMENT -> toDualTrack(
                    mapWithFallback(rawPayload, fallbackPayAmount, fallbackServiceFee, Track.SETTLEMENT_STRICT),
                    OrderDualTrackAmountResolver.resolveStrictSettlement(rawPayload, fallbackPayAmount, fallbackServiceFee));
        };
    }

    public void applyAmounts(
            SyncSource source,
            ColonelsettlementOrder order,
            DualTrackAmounts amounts,
            Map<String, Object> rawPayload) {
        if (amounts == null || order == null) {
            return;
        }
        if (!isPolicyEnabled()) {
            switch (source) {
                case INSTITUTE -> OrderDualTrackAmountResolver.applyInstituteFactToOrder(order, amounts, rawPayload);
                case INSTITUTE_SETTLEMENT -> OrderDualTrackAmountResolver.applyInstituteSettlementToOrder(order, amounts);
                case SETTLEMENT -> OrderDualTrackAmountResolver.applyToOrder(order, amounts);
            }
            return;
        }
        MappedAmounts mapped = toMapped(amounts);
        switch (source) {
            case INSTITUTE -> {
                OrderAmountMapperPolicy.applyInstituteFactToOrder(order, mapped, rawPayload);
                applyInstituteServiceFeeExpenses(order, amounts);
            }
            case INSTITUTE_SETTLEMENT -> OrderDualTrackAmountResolver.applyInstituteSettlementToOrder(order, amounts);
            case SETTLEMENT -> {
                OrderAmountMapperPolicy.applyToOrder(order, mapped);
                applySettlementServiceFeeExpenses(order, amounts);
            }
        }
    }

    public void mergeEstimateSnapshot(ColonelsettlementOrder existing, ColonelsettlementOrder incoming) {
        if (isPolicyEnabled()) {
            OrderAmountMapperPolicy.mergeEstimateSnapshot(existing, incoming);
        } else {
            OrderDualTrackAmountResolver.mergeEstimateSnapshot(existing, incoming);
        }
    }

    public void mergeSettlementSnapshot(ColonelsettlementOrder existing, ColonelsettlementOrder incoming) {
        if (isPolicyEnabled()) {
            OrderAmountMapperPolicy.mergeSettlementSnapshot(existing, incoming);
        } else {
            OrderDualTrackAmountResolver.mergeSettlementSnapshot(existing, incoming);
        }
    }

    /**
     * 订单同步 mapOrder 金额映射入口（DDD-SLIM-ORDER-001）：解析 + 写入 + 6468 结算时间。
     */
    public void mapAndApplyToOrder(
            SyncSource source,
            ColonelsettlementOrder order,
            Map<String, Object> rawPayload,
            Long fallbackPayAmount,
            Long fallbackServiceFee,
            LocalDateTime fallbackSettleTime) {
        if (order == null) {
            return;
        }
        DualTrackAmounts dualTrack = resolveAmounts(source, rawPayload, fallbackPayAmount, fallbackServiceFee);
        applyAmounts(source, order, dualTrack, rawPayload);
        if (source == SyncSource.INSTITUTE) {
            LocalDateTime settleTime = OrderAmountMapperPolicy.resolveInstituteSettleTime(rawPayload, fallbackSettleTime);
            OrderAmountMapperPolicy.applyInstituteSettleTime(order, settleTime);
        }
    }

    private MappedAmounts mapWithFallback(
            Map<String, Object> rawPayload,
            Long fallbackPayAmount,
            Long fallbackServiceFee,
            Track track) {
        ColonelsettlementOrder fallback = new ColonelsettlementOrder();
        if (fallbackPayAmount != null && fallbackPayAmount > 0) {
            fallback.setOrderAmount(fallbackPayAmount);
        }
        if (fallbackServiceFee != null && fallbackServiceFee > 0) {
            fallback.setEstimateServiceFee(fallbackServiceFee);
        }
        return OrderAmountMapperPolicy.map(rawPayload, fallback, null, track);
    }

    private static DualTrackAmounts toDualTrack(MappedAmounts amounts, DualTrackAmounts expenseSource) {
        return new DualTrackAmounts(
                amounts.payAmount(),
                amounts.settleAmount(),
                amounts.estimateServiceFee(),
                amounts.effectiveServiceFee(),
                amounts.estimateTechServiceFee(),
                amounts.effectiveTechServiceFee(),
                expenseSource == null ? 0L : expenseSource.estimateServiceFeeExpense(),
                expenseSource == null ? 0L : expenseSource.effectiveServiceFeeExpense());
    }

    private static MappedAmounts toMapped(DualTrackAmounts amounts) {
        return new MappedAmounts(
                amounts.payAmount(),
                amounts.settleAmount(),
                amounts.estimateServiceFee(),
                amounts.effectiveServiceFee(),
                amounts.estimateTechServiceFee(),
                amounts.effectiveTechServiceFee(),
                null,
                null,
                java.util.List.of(),
                java.util.Map.of());
    }

    private static void applyInstituteServiceFeeExpenses(
            ColonelsettlementOrder order,
            DualTrackAmounts amounts) {
        order.setEstimateServiceFeeExpense(amounts.estimateServiceFeeExpense());
    }

    private static void applySettlementServiceFeeExpenses(
            ColonelsettlementOrder order,
            DualTrackAmounts amounts) {
        order.setEstimateServiceFeeExpense(amounts.estimateServiceFeeExpense());
        order.setEffectiveServiceFeeExpense(amounts.effectiveServiceFeeExpense());
    }
}
