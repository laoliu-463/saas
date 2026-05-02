package com.colonel.saas.controller;

import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
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

import static org.mockito.ArgumentMatchers.anyLong;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(orderSyncService, orderMapper, orderQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getOrderDetail_shouldReturnAggregatedDetail() throws Exception {
        OrderDetailResponse response = new OrderDetailResponse();
        response.setOrderId("mock-order-1");
        response.setAttributionStatus("ATTRIBUTED");
        OrderDetailResponse.PromotionInfo promotion = new OrderDetailResponse.PromotionInfo();
        promotion.setMatched(true);
        response.setPromotion(promotion);
        when(orderQueryService.getOrderDetail("mock-order-1")).thenReturn(response);

        mockMvc.perform(get("/orders/mock-order-1"))
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
}
