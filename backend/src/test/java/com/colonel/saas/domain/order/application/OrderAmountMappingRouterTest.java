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
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getOrderAmountPolicy()).thenReturn(orderAmountPolicySwitch);
        when(orderAmountPolicySwitch.isEnabled()).thenReturn(true);

        ColonelsettlementOrder existing = new ColonelsettlementOrder();
        existing.setEstimateServiceFee(500L);
        existing.setOrderAmount(1000L);

        ColonelsettlementOrder incoming = new ColonelsettlementOrder();
        incoming.setEstimateServiceFee(100L);

        router.mergeEstimateSnapshot(existing, incoming);

        assertThat(incoming.getEstimateServiceFee()).isEqualTo(500L);
    }
}
