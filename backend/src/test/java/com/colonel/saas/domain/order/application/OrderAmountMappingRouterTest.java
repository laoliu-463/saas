package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.OrderDualTrackAmountResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAmountMappingRouterTest {

    @Mock
    private DddRefactorProperties dddRefactorProperties;
    @Mock
    private DddRefactorProperties.Switch orderAmountPolicySwitch;

    private OrderAmountMappingRouter router;

    @BeforeEach
    void setUp() {
        router = new OrderAmountMappingRouter(dddRefactorProperties);
    }

    @Test
    void isPolicyEnabled_requiresRootAndOrderAmountPolicySwitches() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        when(dddRefactorProperties.getOrderAmountPolicy()).thenReturn(orderAmountPolicySwitch);
        when(orderAmountPolicySwitch.isEnabled()).thenReturn(true);
        assertThat(router.isPolicyEnabled()).isFalse();

        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(orderAmountPolicySwitch.isEnabled()).thenReturn(false);
        assertThat(router.isPolicyEnabled()).isFalse();

        when(orderAmountPolicySwitch.isEnabled()).thenReturn(true);
        assertThat(router.isPolicyEnabled()).isTrue();
    }

    @Test
    void resolveAmounts_shouldMatchLegacyWhenPolicyDisabled() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 1000L);
        raw.put("settled_goods_amount", 900L);

        var legacy = OrderDualTrackAmountResolver.resolve(raw, null, null);
        var routed = router.resolveAmounts(
                OrderAmountMappingRouter.SyncSource.INSTITUTE, raw, null, null);

        assertThat(routed).isEqualTo(legacy);
    }

    @Test
    void mergeEstimateSnapshot_shouldPreserveExistingEstimateWhenPolicyEnabled() {
        enableOrderAmountPolicy();

        ColonelsettlementOrder existing = new ColonelsettlementOrder();
        existing.setEstimateServiceFee(500L);
        existing.setOrderAmount(1000L);

        ColonelsettlementOrder incoming = new ColonelsettlementOrder();
        incoming.setEstimateServiceFee(100L);

        router.mergeEstimateSnapshot(existing, incoming);

        assertThat(incoming.getEstimateServiceFee()).isEqualTo(500L);
    }

    @Test
    void resolveAmounts_shouldPreserveServiceFeeExpensesWhenPolicyEnabled() {
        enableOrderAmountPolicy();

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

        var legacy = OrderDualTrackAmountResolver.resolve(raw, null, null);
        var routed = router.resolveAmounts(
                OrderAmountMappingRouter.SyncSource.INSTITUTE, raw, null, null);

        assertThat(routed.estimateServiceFeeExpense()).isEqualTo(legacy.estimateServiceFeeExpense());
        assertThat(routed.effectiveServiceFeeExpense()).isEqualTo(legacy.effectiveServiceFeeExpense());
        assertThat(routed.estimateServiceFeeExpense()).isEqualTo(8L);
        assertThat(routed.effectiveServiceFeeExpense()).isEqualTo(8L);
    }

    @Test
    void applyAmounts_shouldWriteServiceFeeExpensesWhenPolicyEnabled() {
        enableOrderAmountPolicy();

        var amounts = new OrderDualTrackAmountResolver.DualTrackAmounts(
                5000L,
                4800L,
                130L,
                120L,
                4L,
                3L,
                8L,
                7L);
        ColonelsettlementOrder order = new ColonelsettlementOrder();

        router.applyAmounts(
                OrderAmountMappingRouter.SyncSource.SETTLEMENT,
                order,
                amounts,
                Map.of());

        assertThat(order.getEstimateServiceFeeExpense()).isEqualTo(8L);
        assertThat(order.getEffectiveServiceFeeExpense()).isEqualTo(7L);
    }

    @Test
    void mapAndApplyToOrder_shouldWriteInstituteSettleTimeWhenSignalPresent() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("flow_point", "SETTLE");
        raw.put("settle_time", "2026-06-12 10:00:00");
        raw.put("pay_goods_amount", 1000L);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        router.mapAndApplyToOrder(
                OrderAmountMappingRouter.SyncSource.INSTITUTE,
                order,
                raw,
                null,
                null,
                null);

        assertThat(order.getSettleTime()).isNotNull();
        assertThat(order.getOrderAmount()).isEqualTo(1000L);
    }

    private void enableOrderAmountPolicy() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getOrderAmountPolicy()).thenReturn(orderAmountPolicySwitch);
        when(orderAmountPolicySwitch.isEnabled()).thenReturn(true);
    }
}
