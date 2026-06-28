package com.colonel.saas.service;

import com.colonel.saas.domain.order.policy.OrderDualTrackAmountResolver;
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
        raw.put("real_commission", 550L);
        raw.put("estimated_tech_service_fee", 60L);
        raw.put("settled_tech_service_fee", 55L);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(amounts.payAmount()).isEqualTo(5000L);
        assertThat(amounts.settleAmount()).isEqualTo(4800L);
        assertThat(amounts.estimateServiceFee()).isEqualTo(600L);
        assertThat(amounts.effectiveServiceFee()).isEqualTo(550L);
        assertThat(amounts.estimateTechServiceFee()).isEqualTo(60L);
        assertThat(amounts.effectiveTechServiceFee()).isEqualTo(55L);
        assertThat(amounts.estimateServiceFeeExpense()).isEqualTo(0L);
        assertThat(amounts.effectiveServiceFeeExpense()).isEqualTo(0L);
    }

    @Test
    void resolve_shouldUsePrimaryInstitutionWhenBothEstimatedCommissionsExist() {
        // 双机构订单场景：一级、二级都有值时，服务费收入以一级机构为准，二级只作兜底，避免重复计入。
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
    void resolve_serviceFeeExpenseShouldDefaultToZeroWhenNoSecondInstitution() {
        // 单机构或无 colonel_order_info_second 嵌套对象时，服务费支出应为 0
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("estimated_commission", 100L);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(amounts.estimateServiceFeeExpense()).isEqualTo(0L);
        assertThat(amounts.effectiveServiceFeeExpense()).isEqualTo(0L);
    }

    @Test
    void resolve_shouldTreatSecondColonelOverlapAsServiceFeeExpense() {
        // 双机构同时正向：二级 real_commission 计入支出（一级 = 收入，二级 = 支出）
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("settled_goods_amount", 4800L);
        raw.put("colonel_order_info", Map.of(
                "real_commission", 120L,
                "estimated_commission", 130L
        ));
        raw.put("colonel_order_info_second", Map.of(
                "real_commission", 8L,
                "estimated_commission", 8L
        ));

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(amounts.effectiveServiceFee()).isEqualTo(120L);
        assertThat(amounts.estimateServiceFeeExpense()).isEqualTo(8L);
        assertThat(amounts.effectiveServiceFeeExpense()).isEqualTo(8L);
    }

    @Test
    void resolve_shouldNotTreatSecondColonelFallbackAsExpense() {
        // 一级机构为 0（缺失或被清零），二级机构有值 → 不算支出，二级作为有效收入补充
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("settled_goods_amount", 4800L);
        raw.put("colonel_order_info", Map.of("real_commission", 0L));
        raw.put("colonel_order_info_second", Map.of("real_commission", 80L));

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(amounts.effectiveServiceFee()).isEqualTo(80L);
        assertThat(amounts.estimateServiceFeeExpense()).isZero();
        assertThat(amounts.effectiveServiceFeeExpense()).isZero();
    }

    @Test
    void resolve_shouldNotTreatPrimaryNullSecondPositiveAsExpense() {
        // 一级机构 real_commission 为 null（很多真实订单的 SETTLE 样本），
        // 二级机构有值 → 不算支出（避免把独立结算轨误算为支出）。
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("settled_goods_amount", 4800L);
        Map<String, Object> coi = new LinkedHashMap<>();
        coi.put("real_commission", null);
        coi.put("estimated_commission", 100L);
        raw.put("colonel_order_info", coi);
        raw.put("colonel_order_info_second", Map.of("real_commission", 21L));

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(amounts.estimateServiceFeeExpense()).isZero();
        assertThat(amounts.effectiveServiceFeeExpense()).isZero();
    }

    @Test
    void resolve_shouldCalculateOnlyEstimateServiceFeeIncomeFromAmountAndRateWhenFeeFieldsMissing() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 10000L);
        raw.put("settled_goods_amount", 8000L);
        raw.put("service_fee_rate", 10);
        raw.put("estimated_tech_service_fee", 100L);
        raw.put("tech_service_fee", 80L);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(amounts.estimateServiceFee()).isEqualTo(1000L);
        assertThat(amounts.effectiveServiceFee()).isZero();
        assertThat(amounts.estimateTechServiceFee()).isEqualTo(100L);
        assertThat(amounts.effectiveTechServiceFee()).isZero();
    }

    @Test
    void resolve_shouldNotFallbackEffectiveFeeToEstimateInInstituteMode() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("estimated_commission", 600L);
        raw.put("estimated_tech_service_fee", 60L);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts = OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(amounts.estimateServiceFee()).isEqualTo(600L);
        assertThat(amounts.effectiveServiceFee()).isZero();
        assertThat(amounts.estimateTechServiceFee()).isEqualTo(60L);
        assertThat(amounts.effectiveTechServiceFee()).isZero();
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
    void resolveStrictSettlement_shouldNotFallbackSettleAmountToPayAmount() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        raw.put("settled_goods_amount", 0L);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts =
                OrderDualTrackAmountResolver.resolveStrictSettlement(raw, 5000L, null);

        assertThat(amounts.payAmount()).isEqualTo(5000L);
        assertThat(amounts.settleAmount()).isZero();
    }

    @Test
    void resolveStrictSettlement_shouldNotFallbackEffectiveFeeToEstimate() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 5000L);
        Map<String, Object> coi = new LinkedHashMap<>();
        coi.put("estimated_commission", 600L);
        raw.put("colonel_order_info", coi);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts =
                OrderDualTrackAmountResolver.resolveStrictSettlement(raw, null, null);

        assertThat(amounts.estimateServiceFee()).isEqualTo(600L);
        assertThat(amounts.effectiveServiceFee()).isZero();
    }

    @Test
    void resolveStrictSettlement_shouldNotCalculateEffectiveFeeFromRateWithoutSettleAmount() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 10000L);
        raw.put("settled_goods_amount", 0L);
        raw.put("service_fee_rate", 10);

        OrderDualTrackAmountResolver.DualTrackAmounts amounts =
                OrderDualTrackAmountResolver.resolveStrictSettlement(raw, null, null);

        assertThat(amounts.settleAmount()).isZero();
        assertThat(amounts.effectiveServiceFee()).isZero();
    }

    @Test
    void applyInstituteFactToOrder_shouldWriteSettlementWhen6468Settled() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("flow_point", "SETTLE");
        raw.put("settled_goods_amount", 1680L);
        raw.put("colonel_order_info", Map.of("real_commission", 30L, "settled_tech_service_fee", 2L));

        com.colonel.saas.entity.ColonelsettlementOrder order = new com.colonel.saas.entity.ColonelsettlementOrder();
        OrderDualTrackAmountResolver.DualTrackAmounts amounts = new OrderDualTrackAmountResolver.DualTrackAmounts(
                1680L, 1680L, 34L, 30L, 3L, 2L, 0L, 0L);

        OrderDualTrackAmountResolver.applyInstituteFactToOrder(order, amounts, raw);

        assertThat(order.getSettleAmount()).isEqualTo(1680L);
        assertThat(order.getEffectiveServiceFee()).isEqualTo(30L);
        assertThat(order.getEffectiveTechServiceFee()).isEqualTo(2L);
    }

    @Test
    void applyInstituteFactToOrder_shouldNotWriteSettlementForPaySucc() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("flow_point", "PAY_SUCC");
        raw.put("settled_goods_amount", 0L);
        raw.put("colonel_order_info", Map.of("real_commission", 0L, "tech_service_fee", 3L));

        com.colonel.saas.entity.ColonelsettlementOrder order = new com.colonel.saas.entity.ColonelsettlementOrder();
        OrderDualTrackAmountResolver.DualTrackAmounts amounts = new OrderDualTrackAmountResolver.DualTrackAmounts(
                1680L, 1680L, 34L, 0L, 3L, 0L, 0L, 0L);

        OrderDualTrackAmountResolver.applyInstituteFactToOrder(order, amounts, raw);

        assertThat(order.getSettleAmount()).isNull();
        assertThat(order.getEffectiveServiceFee()).isNull();
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

    @Test
    void applyToOrder_shouldNotFallbackLegacySettlementFieldsToEstimate() {
        com.colonel.saas.entity.ColonelsettlementOrder order = new com.colonel.saas.entity.ColonelsettlementOrder();
        OrderDualTrackAmountResolver.DualTrackAmounts amounts = new OrderDualTrackAmountResolver.DualTrackAmounts(
                10_000L, 0L, 1_000L, 0L, 50L, 0L, 0L, 0L);

        OrderDualTrackAmountResolver.applyToOrder(order, amounts);

        assertThat(order.getEffectiveServiceFee()).isZero();
        assertThat(order.getEffectiveTechServiceFee()).isZero();
        assertThat(order.getSettleColonelCommission()).isNull();
        assertThat(order.getSettleColonelTechServiceFee()).isNull();
    }
}
