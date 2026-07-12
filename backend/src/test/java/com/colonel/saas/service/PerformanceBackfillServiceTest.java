package com.colonel.saas.service;

import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.performance.application.PerformanceCalculationApplicationService;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceBackfillServiceTest {

    @Mock
    private OrderReadFacade orderReadFacade;
    @Mock
    private PerformanceCalculationApplicationService performanceCalculationApplicationService;

    private PerformanceBackfillService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceBackfillService(orderReadFacade, performanceCalculationApplicationService);
    }

    @Test
    void backfill_shouldUpsertByOrderIds() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("order-1");
        when(orderReadFacade.findByOrderIds(List.of("order-1"))).thenReturn(List.of(order));
        when(performanceCalculationApplicationService.upsertFromOrder(order)).thenReturn(new PerformanceRecord());

        PerformanceBackfillService.BackfillResult result = service.backfill(
                List.of("order-1"),
                null,
                null,
                null,
                true);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.upserted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(performanceCalculationApplicationService).upsertFromOrder(order);
    }

    @Test
    void reconcileInvalidatedPerformance_shouldUpsertStaleInvalidatedOrders() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("stale-order");
        order.setOrderStatus(OrderCommissionPolicy.STATUS_CANCELLED);
        when(orderReadFacade.findInvalidatedOrdersWithStalePerformance(50)).thenReturn(List.of(order));
        when(performanceCalculationApplicationService.upsertFromOrder(order)).thenReturn(new PerformanceRecord());

        PerformanceBackfillService.BackfillResult result = service.reconcileInvalidatedPerformance(50);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.upserted()).isEqualTo(1);
        verify(performanceCalculationApplicationService).upsertFromOrder(order);
    }

    @Test
    void backfill_shouldScanMissingOrdersWhenOrderIdsEmpty() {
        when(orderReadFacade.findOrdersForBackfill(null, null, true, 100)).thenReturn(List.of());

        PerformanceBackfillService.BackfillResult result = service.backfill(
                null,
                null,
                null,
                100,
                true);

        assertThat(result.scanned()).isZero();
        verify(orderReadFacade).findOrdersForBackfill(null, null, true, 100);
        verify(performanceCalculationApplicationService, never()).upsertFromOrder(any());
    }

    @Test
    void backfill_shouldCountFailureAndContinueWhenSingleOrderFails() {
        ColonelsettlementOrder failingOrder = new ColonelsettlementOrder();
        failingOrder.setOrderId("order-fail");
        ColonelsettlementOrder succeedingOrder = new ColonelsettlementOrder();
        succeedingOrder.setOrderId("order-ok");
        when(orderReadFacade.findByOrderIds(List.of("order-fail", "order-ok")))
                .thenReturn(List.of(failingOrder, succeedingOrder));
        when(performanceCalculationApplicationService.upsertFromOrder(failingOrder))
                .thenThrow(new IllegalStateException("boom"));
        when(performanceCalculationApplicationService.upsertFromOrder(succeedingOrder))
                .thenReturn(new PerformanceRecord());

        PerformanceBackfillService.BackfillResult result = service.backfill(
                List.of("order-fail", "order-ok"),
                null,
                null,
                null,
                false);

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.upserted()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).containsExactly("order-fail: boom");
        verify(performanceCalculationApplicationService).upsertFromOrder(failingOrder);
        verify(performanceCalculationApplicationService).upsertFromOrder(succeedingOrder);
    }
}
