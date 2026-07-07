package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.config.OrderDerivedCacheKeys;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.PerformanceBackfillService;
import com.colonel.saas.service.ShortTtlCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PerformanceOrderAdminControllerTest {

    @Mock private OrderReadFacade orderReadFacade;
    @Mock private OperationLogService operationLogService;
    @Mock private ShortTtlCacheService shortTtlCacheService;
    @Mock private CommissionService commissionService;
    @Mock private PerformanceBackfillService performanceBackfillService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PerformanceOrderAdminController(
                        orderReadFacade,
                        operationLogService,
                        shortTtlCacheService,
                        commissionService,
                        performanceBackfillService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void batchFillCommission_shouldKeepLegacyOrdersPathAndDelegateToPerformanceService() throws Exception {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-1");
        when(orderReadFacade.findByOrderIds(List.of("ORDER-1", "ORDER-2"))).thenReturn(List.of(order));
        when(commissionService.batchUpsertPerformanceRecords(List.of(order)))
                .thenReturn(List.of(CommissionService.OrderCommissionItem.reversed("ORDER-1")));

        mockMvc.perform(post("/orders/commission-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderIds":[" ORDER-1 ","ORDER-1","","ORDER-2"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].orderId").value("ORDER-1"))
                .andExpect(jsonPath("$.data[0].reversed").value(true));

        verify(orderReadFacade).findByOrderIds(List.of("ORDER-1", "ORDER-2"));
        verify(commissionService).batchUpsertPerformanceRecords(List.of(order));
    }

    @Test
    void recalculateSingle_shouldKeepLegacyOrdersPathAndRequireAdmin() throws Exception {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-3");
        when(orderReadFacade.findByOrderId("ORDER-3")).thenReturn(order);
        when(commissionService.batchUpsertPerformanceRecords(List.of(order)))
                .thenReturn(List.of(CommissionService.OrderCommissionItem.reversed("ORDER-3")));

        mockMvc.perform(post("/orders/commission-recalculate")
                        .param("orderId", " ORDER-3 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value("ORDER-3"))
                .andExpect(jsonPath("$.data.reversed").value(true));

        verify(orderReadFacade).findByOrderId("ORDER-3");
        verify(commissionService).batchUpsertPerformanceRecords(List.of(order));

        Method method = PerformanceOrderAdminController.class.getMethod("recalculateSingle", String.class);
        assertThat(method.getAnnotation(RequireRoles.class).value()).containsExactly(RoleCodes.ADMIN);
    }

    @Test
    void performanceBackfill_shouldDelegateToPerformanceBackfillServiceAndEvictDerivedCaches() throws Exception {
        UUID userId = UUID.randomUUID();
        PerformanceBackfillService.BackfillResult result =
                new PerformanceBackfillService.BackfillResult(3, 2, 1, true, List.of("E1"));
        when(performanceBackfillService.backfill(
                eq(List.of("ORDER-1")),
                eq(LocalDateTime.of(2026, 7, 1, 0, 0, 0)),
                eq(LocalDateTime.of(2026, 7, 2, 0, 0, 0)),
                eq(200),
                eq(true))).thenReturn(result);

        mockMvc.perform(post("/orders/performance-backfill")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderIds":["ORDER-1"],
                                  "startTime":"2026-07-01 00:00:00",
                                  "endTime":"2026-07-02 00:00:00",
                                  "limit":200,
                                  "onlyMissing":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanned").value(3))
                .andExpect(jsonPath("$.data.upserted").value(2))
                .andExpect(jsonPath("$.data.failed").value(1));

        verify(operationLogService).recordSystemAction(
                eq(userId), eq("订单业绩"), eq("回填历史业绩记录"), eq("POST"),
                eq("performance_backfill"), isNull(), eq("2026-07-01 00:00:00 ~ 2026-07-02 00:00:00"),
                any());
        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_SUMMARY_PREFIX);
        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_METRICS_PREFIX);
        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.ORDER_STATS_PREFIX);
        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.FILTER_OPTIONS_PREFIX);
    }

    @Test
    void reconcileInvalidatedPerformance_shouldDelegateAndRequireAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        PerformanceBackfillService.BackfillResult result =
                new PerformanceBackfillService.BackfillResult(4, 3, 0, false, List.of());
        when(performanceBackfillService.reconcileInvalidatedPerformance(100)).thenReturn(result);

        mockMvc.perform(post("/orders/performance-reconcile-invalidated")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanned").value(4))
                .andExpect(jsonPath("$.data.upserted").value(3));

        verify(operationLogService).recordSystemAction(
                eq(userId), eq("订单业绩"), eq("重算失效订单过期业绩"), eq("POST"),
                eq("performance_reconcile_invalidated"), isNull(), isNull(), any());

        Method method = PerformanceOrderAdminController.class.getMethod(
                "reconcileInvalidatedPerformance",
                PerformanceOrderAdminController.PerformanceReconcileRequest.class,
                UUID.class);
        assertThat(method.getAnnotation(RequireRoles.class).value()).containsExactly(RoleCodes.ADMIN);
    }
}
