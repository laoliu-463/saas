package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.DashboardService;
import com.colonel.saas.service.OrderAttributionReplayService;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.OrderSyncService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderSyncService orderSyncService;
    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private OrderQueryService orderQueryService;
    @Mock
    private OrderAttributionReplayService orderAttributionReplayService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(orderSyncService, orderMapper, orderQueryService, orderAttributionReplayService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getOrderDetail_shouldReturnAggregatedDetail() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();
        java.util.UUID deptId = java.util.UUID.randomUUID();
        OrderDetailResponse response = new OrderDetailResponse();
        response.setOrderId("mock-order-1");
        response.setAttributionStatus("ATTRIBUTED");
        OrderDetailResponse.PromotionInfo promotion = new OrderDetailResponse.PromotionInfo();
        promotion.setMatched(true);
        response.setPromotion(promotion);
        when(orderQueryService.getOrderDetail("mock-order-1", userId, deptId, DataScope.ALL)).thenReturn(response);

        mockMvc.perform(get("/orders/mock-order-1")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value("mock-order-1"))
                .andExpect(jsonPath("$.data.attributionStatus").value("ATTRIBUTED"))
                .andExpect(jsonPath("$.data.promotion.matched").value(true));
    }

    @Test
    void syncOrders_shouldUseProvidedTimeRange() throws Exception {
        OrderSyncService.SyncResult result = new OrderSyncService.SyncResult(0L, 0L, 1, 1, 0, false);
        when(orderSyncService.syncByTimeRange(anyLong(), anyLong())).thenReturn(result);

        mockMvc.perform(post("/orders/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime":"2026-04-01 00:00:00","endTime":"2026-04-28 23:59:59"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(orderSyncService).syncByTimeRange(1774972800L, 1777391999L);
    }

    @Test
    void syncOrders_shouldFallbackToDefaultWindowWhenBodyMissing() throws Exception {
        OrderSyncService.SyncResult result = new OrderSyncService.SyncResult(0L, 0L, 0, 0, 0, false);
        when(orderSyncService.syncByTimeRange(anyLong(), anyLong())).thenReturn(result);

        mockMvc.perform(post("/orders/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Long> startCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> endCaptor = ArgumentCaptor.forClass(Long.class);
        verify(orderSyncService).syncByTimeRange(startCaptor.capture(), endCaptor.capture());
        Assertions.assertTrue(endCaptor.getValue() >= startCaptor.getValue());
    }

    @Test
    void syncOrders_shouldRequireAdminRoleAnnotation() throws Exception {
        Method syncOrders = OrderController.class.getMethod("syncOrders", OrderController.SyncRequest.class);
        assertThat(syncOrders.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(syncOrders.getAnnotation(RequireRoles.class).value()).containsExactly(RoleCodes.ADMIN);
    }

    @Test
    void replayAttribution_shouldForwardRequestToService() throws Exception {
        OrderAttributionReplayService.ReplayResult result =
                new OrderAttributionReplayService.ReplayResult(12, 4, 8, 0, true, 0, 0, 0, 0, 0, 8);
        when(orderAttributionReplayService.replay(any(), any(), any(), anyBoolean())).thenReturn(result);

        mockMvc.perform(post("/orders/replay-attribution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"COLONEL_MAPPING_NOT_FOUND","limit":12,"dryRun":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.scanned").value(12))
                .andExpect(jsonPath("$.data.attributed").value(4))
                .andExpect(jsonPath("$.data.dryRun").value(true));

        verify(orderAttributionReplayService).replay(any(), org.mockito.ArgumentMatchers.eq("COLONEL_MAPPING_NOT_FOUND"), org.mockito.ArgumentMatchers.eq(12), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void getStats_shouldAggregateViaSqlGroupedMaps() throws Exception {
        when(orderMapper.selectMaps(any())).thenReturn(
                List.of(
                        Map.of("attributionStatus", "ATTRIBUTED", "total", 1L),
                        Map.of("attributionStatus", "UNATTRIBUTED", "total", 2L),
                        Map.of("attributionStatus", "PARTIAL", "total", 1L)
                ),
                List.of(
                        Map.of("reason", "pick_source 未匹配", "total", 1L),
                        Map.of("reason", "SYNC_FAILED", "total", 1L)
                )
        );

        mockMvc.perform(get("/orders/stats")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalOrders").value(4))
                .andExpect(jsonPath("$.data.attributedOrders").value(1))
                .andExpect(jsonPath("$.data.unattributedOrders").value(2))
                .andExpect(jsonPath("$.data.partialOrders").value(1))
                .andExpect(jsonPath("$.data.syncFailedOrders").value(1))
                .andExpect(jsonPath("$.data.unattributedReasons[0].reason").value("pick_source 未匹配"));

        verify(orderMapper, times(2)).selectMaps(any());
    }

    @Test
    void diagnosisSql_shouldReuseDashboardClassifierForOrders() throws Exception {
        OrderController controller = new OrderController(orderSyncService, orderMapper, orderQueryService, orderAttributionReplayService);
        Method method = OrderController.class.getDeclaredMethod("diagnosisSql", String.class, String.class);
        method.setAccessible(true);

        String sql = (String) method.invoke(controller, "colonelsettlement_order.", DashboardService.DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED);

        String expected = "(" + DashboardService.diagnosisCategoryCaseSql(
                "colonelsettlement_order.colonel_activity_id",
                "colonelsettlement_order.second_colonel_activity_id",
                "colonelsettlement_order.product_id",
                "colonelsettlement_order.create_time",
                "colonelsettlement_order.colonel_buyin_id",
                "colonelsettlement_order.attribution_status",
                "colonelsettlement_order.attribution_remark"
        ) + ") = '" + DashboardService.DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED + "'";
        assertThat(sql).isEqualTo(expected);
    }

    @Test
    void diagnosisSql_shouldAcceptUnsafeAlias() throws Exception {
        OrderController controller = new OrderController(orderSyncService, orderMapper, orderQueryService, orderAttributionReplayService);
        Method method = OrderController.class.getDeclaredMethod("diagnosisSql", String.class, String.class);
        method.setAccessible(true);

        String sql = (String) method.invoke(controller, "colonelsettlement_order.", "UNSAFE_BECAUSE_CREATED_AFTER_ORDER");

        assertThat(sql).contains("'" + DashboardService.DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE + "'");
    }

    @Test
    void getFilterOptions_shouldExposeNativeColonelReasonLabels() throws Exception {
        when(orderMapper.selectMaps(any())).thenReturn(
                List.of(Map.of("value", 1)),
                List.of(Map.of("value", "UNATTRIBUTED")),
                List.of(
                        Map.of("value", "COLONEL_MAPPING_NOT_FOUND"),
                        Map.of("value", "COLONEL_MAPPING_AMBIGUOUS")
                ),
                List.of(),
                List.of(),
                List.of()
        );

        mockMvc.perform(get("/orders/filter-options")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.unattributedReasons[0].value").value("COLONEL_MAPPING_NOT_FOUND"))
                .andExpect(jsonPath("$.data.unattributedReasons[0].label").value("原生团长订单未找到归因映射"))
                .andExpect(jsonPath("$.data.unattributedReasons[1].value").value("COLONEL_MAPPING_AMBIGUOUS"))
                .andExpect(jsonPath("$.data.unattributedReasons[1].label").value("原生团长订单命中多条归因映射"));
    }
}
