package com.colonel.saas.controller;

import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.service.OrderSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderSyncControllerTest {

    @Mock
    private OrderSyncService orderSyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderSyncController(orderSyncService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void triggerSync_shouldReturnResultForApiPath() throws Exception {
        when(orderSyncService.triggerManualSync())
                .thenReturn(new OrderSyncService.SyncResult(100, 200, 1, 2, 0, false));

        mockMvc.perform(post("/order-sync-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.inserted").value(2));
    }

    @Test
    void triggerSync_shouldReturnResultForOrdersSyncAlias() throws Exception {
        when(orderSyncService.triggerManualSync())
                .thenReturn(new OrderSyncService.SyncResult(100, 200, 1, 3, 1, false));

        mockMvc.perform(post("/orders/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.inserted").value(3));
    }
}
