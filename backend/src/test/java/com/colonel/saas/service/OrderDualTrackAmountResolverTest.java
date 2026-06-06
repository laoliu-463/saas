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
        assertThat(amounts.effectiveServiceFee()).isEqualTo(495L);
        assertThat(amounts.estimateTechServiceFee()).isEqualTo(60L);
        assertThat(amounts.effectiveTechServiceFee()).isEqualTo(55L);
        assertThat(amounts.estimateServiceFeeExpense()).isEqualTo(0L);
        assertThat(amounts.effectiveServiceFeeExpense()).isEqualTo(0L);
    }

    @Test
    void resolve_shouldPreferFirstInstitutionWhenBothEstimatedCommissionsExist() {
        // 双机构订单场景：一级、二级都存在时，服务费收入不能重复累加
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("settled_goods_amount", 4800L);

        Map<String, Object> coi = new LinkedHashMap<>();
        coi.put("estimated_commission", 120L);
        coi.put("tech_service_fee", 12L);
        raw.put("colonel_order_info", coi);

        Map<String, Object> coi2 = new LinkedHashMap<>();
        coi2.put("estimated_commission", 95L);
        raw.put("colonel_order_info_second", coi2);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        // 服务费收入 = COI(120)，COI2(95) 不重复计入
        assertThat(amounts.estimateServiceFee()).isEqualTo(120L);
        assertThat(amounts.estimateTechServiceFee()).isEqualTo(12L);
    }

    @Test
    void resolve_shouldFallbackToCoi2WhenCoiCommissionIsNull() {
        // COI.estimated_commission 为 null，COI2 有值，应取 COI2
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 3368L);

        Map<String, Object> coi = new LinkedHashMap<>();
        coi.put("estimated_commission", null);
        coi.put("tech_service_fee", null);
        raw.put("colonel_order_info", coi);

        Map<String, Object> coi2 = new LinkedHashMap<>();
        coi2.put("estimated_commission", 89L);
        raw.put("colonel_order_info_second", coi2);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        // COI.ec=null 不贡献，COI2.ec=89，总收入=89
        assertThat(amounts.estimateServiceFee()).isEqualTo(89L);
        assertThat(amounts.estimateTechServiceFee()).isEqualTo(0L);
    }

    @Test
    void resolve_serviceFeeExpenseShouldDefaultToZero() {
        // 服务费支出当前无 raw payload 字段，应默认为 0
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("estimated_commission", 100L);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(amounts.estimateServiceFeeExpense()).isEqualTo(0L);
        assertThat(amounts.effectiveServiceFeeExpense()).isEqualTo(0L);
    }

    @Test
    void resolve_shouldCalculateServiceFeeIncomeFromAmountAndRateWhenFeeFieldsMissing() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 10000L);
        raw.put("settled_goods_amount", 8000L);
        raw.put("service_fee_rate", 10);
        raw.put("estimated_tech_service_fee", 100L);
        raw.put("tech_service_fee", 80L);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(amounts.estimateServiceFee()).isEqualTo(1000L);
        assertThat(amounts.effectiveServiceFee()).isEqualTo(720L);
        assertThat(amounts.estimateTechServiceFee()).isEqualTo(100L);
        assertThat(amounts.effectiveTechServiceFee()).isEqualTo(80L);
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

    @Test
    void mergeSettlementSnapshot_shouldPreserveExistingSettlementOnUpdate() {
        com.colonel.saas.entity.ColonelsettlementOrder existing = new com.colonel.saas.entity.ColonelsettlementOrder();
        existing.setSettleAmount(2480L);
        existing.setEffectiveServiceFee(50L);
        existing.setEffectiveTechServiceFee(6L);

        com.colonel.saas.entity.ColonelsettlementOrder incoming = new com.colonel.saas.entity.ColonelsettlementOrder();
        incoming.setOrderAmount(2550L);
        incoming.setEstimateServiceFee(55L);
        incoming.setEstimateTechServiceFee(7L);
        // incoming settlement fields are null or 0

        OrderDualTrackAmountResolver.mergeSettlementSnapshot(existing, incoming);

        // Settlement fields preserved from existing
        assertThat(incoming.getSettleAmount()).isEqualTo(2480L);
        assertThat(incoming.getEffectiveServiceFee()).isEqualTo(50L);
        assertThat(incoming.getEffectiveTechServiceFee()).isEqualTo(6L);
        // Estimate fields untouched
        assertThat(incoming.getOrderAmount()).isEqualTo(2550L);
        assertThat(incoming.getEstimateServiceFee()).isEqualTo(55L);
    }

    @Test
    void mergeSettlementSnapshot_shouldNotOverwriteWhenIncomingHasValue() {
        com.colonel.saas.entity.ColonelsettlementOrder existing = new com.colonel.saas.entity.ColonelsettlementOrder();
        existing.setSettleAmount(2480L);
        existing.setEffectiveServiceFee(50L);
        existing.setEffectiveTechServiceFee(6L);

        com.colonel.saas.entity.ColonelsettlementOrder incoming = new com.colonel.saas.entity.ColonelsettlementOrder();
        incoming.setSettleAmount(2500L);
        incoming.setEffectiveServiceFee(55L);
        incoming.setEffectiveTechServiceFee(7L);

        OrderDualTrackAmountResolver.mergeSettlementSnapshot(existing, incoming);

        // Incoming already has values, should NOT be overwritten by existing
        assertThat(incoming.getSettleAmount()).isEqualTo(2500L);
        assertThat(incoming.getEffectiveServiceFee()).isEqualTo(55L);
        assertThat(incoming.getEffectiveTechServiceFee()).isEqualTo(7L);
    }
}
