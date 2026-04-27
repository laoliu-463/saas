package com.colonel.saas.controller;

import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.OrderSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
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
}
