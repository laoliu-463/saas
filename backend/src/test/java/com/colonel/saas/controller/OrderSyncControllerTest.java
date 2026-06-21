package com.colonel.saas.controller;

import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.facade.OrderDomainFacade;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.service.DashboardService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.OrderAttributionReplayService;
import com.colonel.saas.service.Order1603SettlementDryRunService;
import com.colonel.saas.service.Order2704SettlementDryRunService;
import com.colonel.saas.service.Order6468PaginationDryRunService;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.OrderService;
import com.colonel.saas.service.OrderSyncService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderSyncControllerTest {

    @Mock
    private OrderSyncService orderSyncService;
    @Mock
    private Order6468PaginationDryRunService order6468PaginationDryRunService;
    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private OrderQueryService orderQueryService;
    @Mock
    private OrderAttributionReplayService orderAttributionReplayService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private CommissionService commissionService;
    @Mock
    private PerformanceBackfillService performanceBackfillService;
    @Mock
    private UserDomainFacade userDomainFacade;
    @Mock
    private Order1603SettlementDryRunService order1603SettlementDryRunService;
    @Mock
    private Order2704SettlementDryRunService order2704SettlementDryRunService;
    @Mock
    private ProductSnapshotMapper productSnapshotMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private DddRefactorProperties dddRefactorProperties;
    @Mock
    private OrderDomainFacade orderDomainFacade;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // t2-orders 抽 service：OrderController 现在依赖 OrderService（t2 抽 service 后）。
        // 本测试只覆盖 /orders/sync 端点，OrderService 不会被调用，但仍需传入真实实例。
        DashboardService dashboardService = org.mockito.Mockito.mock(DashboardService.class);
        DataScopePolicy dataScopePolicy = new DataScopePolicy();
        OrderService orderService = new OrderService(
                orderMapper, dashboardService, productSnapshotMapper, productMapper, dataScopePolicy, new com.colonel.saas.config.DddRefactorProperties());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(
                        orderSyncService,
                        orderMapper,
                        orderQueryService,
                        orderAttributionReplayService,
                        operationLogService,
                        new ShortTtlCacheService(),
                        commissionService,
                        performanceBackfillService,
                        userDomainFacade,
                        order6468PaginationDryRunService,
                        order1603SettlementDryRunService,
                        order2704SettlementDryRunService,
                        orderService,
                        dddRefactorProperties,
                        orderDomainFacade,
                        dataScopePolicy))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void syncOrders_shouldReturnManualSyncResult() throws Exception {
        when(orderSyncService.syncInstituteOrdersHotRecent())
                .thenReturn(new OrderSyncService.SyncResult(1714492800L, 1714496400L, 1, 2, 0, false));

        mockMvc.perform(post("/orders/sync")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "2024-05-01 00:00:00",
                                  "endTime": "2024-05-01 01:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.created").value(2));

        verify(orderSyncService).syncInstituteOrdersHotRecent();
        verify(orderSyncService, never()).syncByTimeRange(anyLong(), anyLong());
    }

    @Test
    void syncOrdersByRange_shouldCallExplicitTimeRangeSync() throws Exception {
        when(orderSyncService.syncByTimeRange(1781193600L, 1781280000L))
                .thenReturn(new OrderSyncService.SyncResult(
                        1781193600L, 1781280000L, 10, 9201, 190, 9011, 0, 9201, 0, false));

        mockMvc.perform(post("/orders/sync-range")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "2026-06-12 00:00:00",
                                  "endTime": "2026-06-13 00:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.created").value(190))
                .andExpect(jsonPath("$.data.updated").value(9011));

        verify(orderSyncService, never()).syncInstituteOrdersHotRecent();
        verify(orderSyncService).syncByTimeRange(eq(1781193600L), eq(1781280000L));
    }

    @Test
    void dryRun2704Settlement_shouldReturnReadonlyProbeResult() throws Exception {
        when(order2704SettlementDryRunService.dryRun(any()))
                .thenReturn(new Order2704SettlementDryRunService.DryRunResult(
                        Order2704SettlementDryRunService.SOURCE,
                        Order2704SettlementDryRunService.API_METHOD,
                        "settle",
                        "2026-06-12 00:00:00",
                        "2026-06-13 00:00:00",
                        100,
                        2,
                        2,
                        2,
                        0,
                        "NO_NEXT_CURSOR",
                        "0",
                        true,
                        new Order2704SettlementDryRunService.AmountSummary(3000L, 60L, 6L, 9L),
                        Map.of("service_fee_expense", 9L),
                        new Order2704SettlementDryRunService.DiffSummary(
                                1L,
                                1L,
                                0L,
                                List.of("ORDER-2"),
                                List.of()),
                        List.of("order_id", "service_fee_expense"),
                        List.of()));

        mockMvc.perform(post("/orders/2704-settlement-dry-run")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "2026-06-12 00:00:00",
                                  "endTime": "2026-06-13 00:00:00",
                                  "timeType": "settle",
                                  "pageSize": 100,
                                  "maxPages": 500,
                                  "maxOrders": 50000,
                                  "maxDiffOrderIds": 500
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.readOnly").value(true))
                .andExpect(jsonPath("$.data.apiMethod").value("buyin.colonelMultiSettlementOrders"))
                .andExpect(jsonPath("$.data.summary.serviceFeeExpenseCent").value(9))
                .andExpect(jsonPath("$.data.diff.onlyInUpstream").value(1))
                .andExpect(jsonPath("$.data.diff.onlyInUpstreamOrderIds[0]").value("ORDER-2"));

        verify(order2704SettlementDryRunService)
                .dryRun(any(Order2704SettlementDryRunService.DryRunRequest.class));
        verify(orderMapper, never()).insert(any());
    }
}
