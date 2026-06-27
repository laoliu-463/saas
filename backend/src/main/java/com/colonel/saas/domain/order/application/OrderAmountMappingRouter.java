package com.colonel.saas.domain.order.application;

import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy;
import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy.MappedAmounts;
import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy.Track;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单金额映射路由（DDD-ORDER-002）：全量委派 {@link OrderAmountMapperPolicy}。
 *
 * <p>原设计在 legacy {@code OrderDualTrackAmountResolver} 与 Policy 之间按灰度开关切换。
 * 目前 Policy 已功能对等且通过验证，全量走 Policy 路径。</p>
 */
@Service
public class OrderAmountMappingRouter {

    private final DddRefactorProperties dddRefactorProperties;

    public OrderAmountMappingRouter(DddRefactorProperties dddRefactorProperties) {
        this.dddRefactorProperties = dddRefactorProperties;
    }

    public enum SyncSource {
        INSTITUTE,
        INSTITUTE_SETTLEMENT,
        SETTLEMENT
    }

    /**
     * 解析双轨金额（全量走 Policy）。
     */
    public MappedAmounts resolveAmounts(
            SyncSource source,
            Map<String, Object> rawPayload,
            Long fallbackPayAmount,
            Long fallbackServiceFee) {
        return switch (source) {
            case INSTITUTE -> mapWithFallback(rawPayload, fallbackPayAmount, fallbackServiceFee, Track.INSTITUTE);
            case INSTITUTE_SETTLEMENT -> OrderAmountMapperPolicy.mapInstituteSettlement(rawPayload);
            case SETTLEMENT -> mapWithFallback(rawPayload, fallbackPayAmount, fallbackServiceFee, Track.SETTLEMENT_STRICT);
        };
    }

    /**
     * 将映射结果写入订单实体（全量走 Policy）。
     */
    public void applyAmounts(
            SyncSource source,
            ColonelsettlementOrder order,
            MappedAmounts amounts,
            Map<String, Object> rawPayload) {
        if (amounts == null || order == null) {
            return;
        }
        switch (source) {
            case INSTITUTE -> OrderAmountMapperPolicy.applyInstituteFactToOrder(order, amounts, rawPayload);
            case INSTITUTE_SETTLEMENT -> OrderAmountMapperPolicy.applyInstituteSettlementToOrder(order, amounts);
            case SETTLEMENT -> OrderAmountMapperPolicy.applyToOrder(order, amounts);
        }
    }

    public void mergeEstimateSnapshot(ColonelsettlementOrder existing, ColonelsettlementOrder incoming) {
        OrderAmountMapperPolicy.mergeEstimateSnapshot(existing, incoming);
    }

    public void mergeSettlementSnapshot(ColonelsettlementOrder existing, ColonelsettlementOrder incoming) {
        OrderAmountMapperPolicy.mergeSettlementSnapshot(existing, incoming);
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
        MappedAmounts amounts = resolveAmounts(source, rawPayload, fallbackPayAmount, fallbackServiceFee);
        applyAmounts(source, order, amounts, rawPayload);
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
}
