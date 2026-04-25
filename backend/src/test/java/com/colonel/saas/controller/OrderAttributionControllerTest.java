package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderAttributionControllerTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderAttributionController(orderMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getUnattributedOrders_shouldReturnPage() throws Exception {
        ColonelsettlementOrder row = new ColonelsettlementOrder();
        row.setOrderId("mock-order-1");
        row.setProductId("product-1");
        row.setAttributionStatus("UNATTRIBUTED");
        row.setAttributionRemark("pick_source 未匹配");
        row.setCreateTime(LocalDateTime.of(2026, 4, 25, 10, 0));

        IPage<ColonelsettlementOrder> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(row));
        when(orderMapper.findPageWithScope(any(Page.class), any())).thenReturn(page);

        mockMvc.perform(get("/orders/order-attribution-unattributed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].orderId").value("mock-order-1"))
                .andExpect(jsonPath("$.data.records[0].attributionStatus").value("UNATTRIBUTED"));
    }

    @Test
    void getSummary_shouldReturnAggregatedCounts() throws Exception {
        ColonelsettlementOrder attributed = new ColonelsettlementOrder();
        attributed.setOrderId("a-1");
        attributed.setOrderAmount(1000L);
        attributed.setSettleColonelCommission(100L);
        attributed.setAttributionStatus("ATTRIBUTED");
        attributed.setChannelUserId(UUID.randomUUID());
        attributed.setUserId(UUID.randomUUID());
        attributed.setCreateTime(LocalDateTime.of(2026, 4, 25, 10, 0));

        ColonelsettlementOrder unattributed = new ColonelsettlementOrder();
        unattributed.setOrderId("u-1");
        unattributed.setOrderAmount(2000L);
        unattributed.setSettleColonelCommission(200L);
        unattributed.setAttributionStatus("UNATTRIBUTED");
        unattributed.setCreateTime(LocalDateTime.of(2026, 4, 25, 11, 0));

        when(orderMapper.selectList(any())).thenReturn(List.of(attributed, unattributed));

        mockMvc.perform(get("/dashboard/order-attribution-summary")
                        .requestAttr("userId", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.attributedOrderCount").value(1))
                .andExpect(jsonPath("$.data.unattributedOrderCount").value(1));
    }
}
