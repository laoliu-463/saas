package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy.MappedAmounts;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OrderAmountMappingRouterTest {

    @Mock
    private DddRefactorProperties dddRefactorProperties;

    private OrderAmountMappingRouter router;

    @BeforeEach
    void setUp() {
        router = new OrderAmountMappingRouter(dddRefactorProperties);
    }

    @Test
    void resolveAmounts_shouldUsePolicyToResolve() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pay_goods_amount", 1000L);
        raw.put("settled_goods_amount", 900L);

        MappedAmounts routed = router.resolveAmounts(
                OrderAmountMappingRouter.SyncSource.INSTITUTE, raw, null, null);

        assertThat(routed.payAmount()).isEqualTo(1000L);
        assertThat(routed.settleAmount()).isEqualTo(900L);
    }

    @Test
    void mergeEstimateSnapshot_shouldPreserveExistingEstimate() {
        ColonelsettlementOrder existing = new ColonelsettlementOrder();
        existing.setEstimateServiceFee(500L);
        existing.setOrderAmount(1000L);

        ColonelsettlementOrder incoming = new ColonelsettlementOrder();
        incoming.setEstimateServiceFee(100L);

        router.mergeEstimateSnapshot(existing, incoming);

        assertThat(incoming.getEstimateServiceFee()).isEqualTo(500L);
    }

    @Test
    void mapAndApplyToOrder_shouldWriteInstituteSettleTimeWhenSignalPresent() {
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
}
