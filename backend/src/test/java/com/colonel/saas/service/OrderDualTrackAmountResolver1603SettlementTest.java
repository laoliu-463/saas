package com.colonel.saas.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDualTrackAmountResolver1603SettlementTest {

    @Test
    void resolveInstituteSettlement_shouldMap1603SettlementFieldsWithoutHardFallback() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("settled_goods_amount", 4800L);
        raw.put("estimated_commission", 600L);
        raw.put("real_commission", 550L);
        raw.put("estimated_tech_service_fee", 60L);
        raw.put("tech_service_fee", 55L);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts =
                OrderDualTrackAmountResolver.resolveInstituteSettlement(raw);

        assertThat(amounts.payAmount()).isEqualTo(5000L);
        assertThat(amounts.settleAmount()).isEqualTo(4800L);
        assertThat(amounts.estimateServiceFee()).isEqualTo(600L);
        assertThat(amounts.effectiveServiceFee()).isEqualTo(550L);
        assertThat(amounts.estimateTechServiceFee()).isEqualTo(60L);
        assertThat(amounts.effectiveTechServiceFee()).isEqualTo(55L);
    }

    @Test
    void resolveInstituteSettlement_shouldNotFallbackSettleOrEffectiveFields() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("estimated_commission", 600L);
        raw.put("estimated_tech_service_fee", 60L);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts =
                OrderDualTrackAmountResolver.resolveInstituteSettlement(raw);

        assertThat(amounts.payAmount()).isEqualTo(5000L);
        assertThat(amounts.settleAmount()).isZero();
        assertThat(amounts.estimateServiceFee()).isEqualTo(600L);
        assertThat(amounts.effectiveServiceFee()).isZero();
        assertThat(amounts.effectiveTechServiceFee()).isZero();
    }
}
