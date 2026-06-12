package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.AttributionService.AttributionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private DddRefactorProperties dddRefactorProperties;
    private OrderAttributionRouter router;

    @BeforeEach
    void setUp() {
        dddRefactorProperties = new DddRefactorProperties();
        router = new OrderAttributionRouter(dddRefactorProperties, attributionService);
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
}
