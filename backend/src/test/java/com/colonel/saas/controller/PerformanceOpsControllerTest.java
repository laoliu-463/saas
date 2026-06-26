package com.colonel.saas.controller;

import com.colonel.saas.config.OrderDerivedCacheKeys;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.PerformanceBackfillService;
import com.colonel.saas.service.ShortTtlCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PerformanceOpsControllerTest {

    @Mock
    private OrderReadFacade orderReadFacade;
    @Mock
    private CommissionService commissionService;
    @Mock
    private PerformanceBackfillService performanceBackfillService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private ShortTtlCacheService shortTtlCacheService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PerformanceOpsController(
                        orderReadFacade,
                        commissionService,
                        performanceBackfillService,
                        operationLogService,
                        shortTtlCacheService))
                .build();
    }

    @Test
    void performanceBackfill_shouldExposeCanonicalPerformanceRoute() throws Exception {
        UUID userId = UUID.randomUUID();
        PerformanceBackfillService.BackfillResult result =
                new PerformanceBackfillService.BackfillResult(2, 2, 0, true, List.of());
        when(performanceBackfillService.backfill(any(), any(), any(), eq(20), eq(true)))
                .thenReturn(result);

        mockMvc.perform(post("/performance/backfill")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderIds": ["ORDER-1"],
                                  "limit": 20,
                                  "onlyMissing": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.scanned").value(2))
                .andExpect(jsonPath("$.data.upserted").value(2))
                .andExpect(jsonPath("$.data.onlyMissing").value(true));

        verify(performanceBackfillService).backfill(eq(List.of("ORDER-1")), any(), any(), eq(20), eq(true));
        verify(operationLogService).recordSystemAction(
                eq(userId),
                eq("业绩运维"),
                eq("回填历史业绩记录"),
                eq("POST"),
                eq("performance_backfill"),
                any(),
                any(),
                any());
        verifyDerivedCacheEvicted();
    }

    @Test
    void performanceBackfill_shouldKeepLegacyOrdersRouteAsCompatibilityEntry() throws Exception {
        UUID userId = UUID.randomUUID();
        when(performanceBackfillService.backfill(any(), any(), any(), any(), eq(true)))
                .thenReturn(new PerformanceBackfillService.BackfillResult(0, 0, 0, true, List.of()));

        mockMvc.perform(post("/orders/performance-backfill")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(performanceBackfillService).backfill(any(), any(), any(), any(), eq(true));
    }

    @Test
    void batchFillCommission_shouldNormalizeOrderIdsAndDelegateToCommissionService() throws Exception {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-1");
        when(orderReadFacade.findByOrderIds(eq(List.of("ORDER-1")))).thenReturn(List.of(order));
        when(commissionService.batchUpsertPerformanceRecords(any()))
                .thenReturn(List.of(CommissionService.OrderCommissionItem.reversed("ORDER-1")));

        mockMvc.perform(post("/performance/commission-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderIds": [" ORDER-1 ", "ORDER-1", ""]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].orderId").value("ORDER-1"))
                .andExpect(jsonPath("$.data[0].reversed").value(true));

        ArgumentCaptor<List<ColonelsettlementOrder>> ordersCaptor = ArgumentCaptor.forClass(List.class);
        verify(commissionService).batchUpsertPerformanceRecords(ordersCaptor.capture());
        assertThat(ordersCaptor.getValue()).containsExactly(order);
        verifyDerivedCacheEvicted();
    }

    @Test
    void recalculateSingle_shouldReturnReversedWhenOrderMissing() throws Exception {
        when(orderReadFacade.findByOrderIds(eq(List.of("ORDER-MISSING")))).thenReturn(List.of());

        mockMvc.perform(post("/orders/commission-recalculate")
                        .param("orderId", " ORDER-MISSING "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value("ORDER-MISSING"))
                .andExpect(jsonPath("$.data.reversed").value(true));

        verify(commissionService, never()).batchUpsertPerformanceRecords(any());
    }

    private void verifyDerivedCacheEvicted() {
        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_SUMMARY_PREFIX);
        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_METRICS_PREFIX);
        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.ORDER_STATS_PREFIX);
        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.FILTER_OPTIONS_PREFIX);
    }

}
