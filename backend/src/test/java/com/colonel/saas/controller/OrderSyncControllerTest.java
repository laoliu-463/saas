package com.colonel.saas.controller;

import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.OrderAttributionReplayService;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.CommissionService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderSyncControllerTest {

    @Mock
    private OrderSyncService orderSyncService;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(
                        orderSyncService,
                        orderMapper,
                        orderQueryService,
                        orderAttributionReplayService,
                        operationLogService,
                        new ShortTtlCacheService(),
                        commissionService,
                        performanceBackfillService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void syncOrders_shouldReturnManualSyncResult() throws Exception {
        when(orderSyncService.syncByTimeRange(eq(1714492800L), eq(1714496400L)))
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
    }
}
