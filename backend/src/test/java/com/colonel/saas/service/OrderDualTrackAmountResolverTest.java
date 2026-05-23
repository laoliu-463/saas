package com.colonel.saas.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDualTrackAmountResolverTest {

    @Test
    void resolve_shouldMapEstimateAndEffectiveTracks() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("settled_goods_amount", 4800L);
        raw.put("estimated_commission", 600L);
        raw.put("commission", 550L);
        raw.put("estimated_tech_service_fee", 60L);
        raw.put("tech_service_fee", 55L);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(amounts.payAmount()).isEqualTo(5000L);
        assertThat(amounts.settleAmount()).isEqualTo(4800L);
        assertThat(amounts.estimateServiceFee()).isEqualTo(600L);
        assertThat(amounts.effectiveServiceFee()).isEqualTo(550L);
        assertThat(amounts.estimateTechServiceFee()).isEqualTo(60L);
        assertThat(amounts.effectiveTechServiceFee()).isEqualTo(55L);
    }

    @Test
    void mergeEstimateSnapshot_shouldPreserveExistingEstimateOnUpdate() {
        com.colonel.saas.entity.ColonelsettlementOrder existing = new com.colonel.saas.entity.ColonelsettlementOrder();
        existing.setEstimateServiceFee(900L);
        existing.setEstimateTechServiceFee(90L);
        existing.setOrderAmount(3000L);

        com.colonel.saas.entity.ColonelsettlementOrder incoming = new com.colonel.saas.entity.ColonelsettlementOrder();
        incoming.setEstimateServiceFee(0L);
        incoming.setEffectiveServiceFee(800L);

        OrderDualTrackAmountResolver.mergeEstimateSnapshot(existing, incoming);

        assertThat(incoming.getEstimateServiceFee()).isEqualTo(900L);
        assertThat(incoming.getEstimateTechServiceFee()).isEqualTo(90L);
        assertThat(incoming.getOrderAmount()).isEqualTo(3000L);
    }
}
