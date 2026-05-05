package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class OrderAttributionControllerTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new com.colonel.saas.controller.OrderAttributionController(orderMapper))
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
        UUID deptId = UUID.randomUUID();

        mockMvc.perform(get("/orders/order-attribution-unattributed")
                        .requestAttr("userId", UUID.randomUUID())
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.DEPT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].orderId").value("mock-order-1"))
                .andExpect(jsonPath("$.data.records[0].attributionStatus").value("UNATTRIBUTED"));

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        Assertions.assertNotNull(sqlSegment);
        Assertions.assertTrue(sqlSegment.contains("co.dept_id"), sqlSegment);
    }

    @Test
    void getSummary_shouldReturnAggregatedCounts() throws Exception {
        UUID channelUserId = UUID.randomUUID();
        UUID colonelUserId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of(
                        "order_count", 2L,
                        "order_amount_cent", 3000L,
                        "service_fee_cent", 300L,
                        "attributed_order_count", 1L,
                        "unattributed_order_count", 1L
                )))
                .thenReturn(List.of(Map.of(
                        "owner_id", channelUserId.toString(),
                        "order_count", 1L,
                        "order_amount_cent", 1000L,
                        "service_fee_cent", 100L
                )))
                .thenReturn(List.of(Map.of(
                        "owner_id", colonelUserId.toString(),
                        "order_count", 1L,
                        "order_amount_cent", 1000L,
                        "service_fee_cent", 100L
                )));

        mockMvc.perform(get("/dashboard/order-attribution-summary")
                        .requestAttr("userId", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.attributedOrderCount").value(1))
                .andExpect(jsonPath("$.data.unattributedOrderCount").value(1))
                .andExpect(jsonPath("$.data.channelPerformance[0].ownerId").value(channelUserId.toString()))
                .andExpect(jsonPath("$.data.colonelPerformance[0].ownerId").value(colonelUserId.toString()));
    }

    @Test
    void getSummary_shouldGroupColonelPerformanceByColonelUserId() throws Exception {
        UUID colonelUserId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of(
                        "order_count", 1L,
                        "order_amount_cent", 1000L,
                        "service_fee_cent", 100L,
                        "attributed_order_count", 1L,
                        "unattributed_order_count", 0L
                )))
                .thenReturn(List.of())
                .thenReturn(List.of(Map.of(
                        "owner_id", colonelUserId.toString(),
                        "order_count", 1L,
                        "order_amount_cent", 1000L,
                        "service_fee_cent", 100L
                )));

        mockMvc.perform(get("/dashboard/order-attribution-summary")
                        .requestAttr("userId", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colonelPerformance[0].ownerId").value(colonelUserId.toString()));

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper, atLeastOnce()).selectMaps(wrapperCaptor.capture());
        boolean usesColonelOwner = wrapperCaptor.getAllValues().stream()
                .map(QueryWrapper::getSqlSelect)
                .anyMatch(sql -> sql != null && sql.contains("colonel_user_id AS owner_id"));
        assertThat(usesColonelOwner).isTrue();
    }
}
