package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.policy.OrderAttributionInput;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionPolicy;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionResult;
import com.colonel.saas.service.AttributionService.AttributionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAttributionRouterTest {

    @Mock
    private AttributionService attributionService;
    @Mock
    private OrderDefaultAttributionResolver defaultAttributionResolver;

    private DddRefactorProperties dddRefactorProperties;
    private OrderAttributionRouter router;

    @BeforeEach
    void setUp() {
        dddRefactorProperties = new DddRefactorProperties();
        router = new OrderAttributionRouter(dddRefactorProperties, attributionService, defaultAttributionResolver);
    }

    @Test
    void resolveAndApply_shouldDelegateToAttributionServiceAndWriteOrder() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductName("商品A");
        UUID channelUserId = UUID.randomUUID();
        AttributionResult result = AttributionResult.attributed(
                channelUserId,
                channelUserId,
                channelUserId,
                null,
                null,
                "act-1",
                null,
                AttributionService.REASON_ATTRIBUTED,
                AttributionService.NativeMappingTrace.none());
        when(attributionService.resolveAttribution(any(), any())).thenReturn(result);

        AttributionResult applied = router.resolveAndApply(order, Map.of(), "达人A");

        verify(attributionService).resolveAttribution(order, Map.of());
        assertThat(applied).isSameAs(result);
        assertThat(order.getChannelUserId()).isEqualTo(channelUserId);
        assertThat(order.getAttributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
    }

    @Test
    void isPolicyEnabled_requiresRootAndOrderAttributionSwitches() {
        assertThat(router.isPolicyEnabled()).isFalse();

        dddRefactorProperties.setEnabled(true);
        assertThat(router.isPolicyEnabled()).isFalse();

        dddRefactorProperties.getOrderAttribution().setEnabled(true);
        assertThat(router.isPolicyEnabled()).isTrue();
    }

    @Test
    void resolveAndApply_whenPolicyEnabled_shouldUseDefaultAttributionResolver() {
        dddRefactorProperties.setEnabled(true);
        dddRefactorProperties.getOrderAttribution().setEnabled(true);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        UUID channelUserId = UUID.randomUUID();
        LocalDateTime mappingCreatedAt = LocalDateTime.of(2026, 5, 10, 6, 41, 19);
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(channelUserId);
        mapping.setDeptId(channelUserId);
        mapping.setActivityId("act-1");
        mapping.setCreateTime(mappingCreatedAt);
        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                new OrderAttributionInput("product-1", "act-1", "pick-1", null, null, null),
                mapping,
                new OrderDefaultAttributionPolicy.RecruiterLookup(null, null, false));
        when(defaultAttributionResolver.resolveWithTrace(any(), any())).thenReturn(
                new OrderDefaultAttributionResolver.Resolution(result, true, mappingCreatedAt));

        AttributionResult applied = router.resolveAndApply(order, Map.of(), "达人A");

        verify(defaultAttributionResolver).resolveWithTrace(order, Map.of());
        assertThat(applied.channelUserId()).isEqualTo(channelUserId);
        assertThat(applied.nativeTrace().nativeKeyMatched()).isTrue();
        assertThat(applied.nativeTrace().mappingCreatedAt()).isEqualTo(mappingCreatedAt);
        assertThat(order.getChannelUserId()).isEqualTo(channelUserId);
    }
}
