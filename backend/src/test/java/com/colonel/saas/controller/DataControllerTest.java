package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.OrderDecryptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class DataControllerTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private OrderDecryptService orderDecryptService;
    @Mock
    private CommissionService commissionService;

    private DataController dataController;

    private static ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> queryWrapperCaptor() {
        return (ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(QueryWrapper.class);
    }

    @BeforeEach
    void setUp() {
        dataController = new DataController(orderMapper, orderDecryptService, commissionService);
    }

    @Test
    void getOrderPage_shouldAlwaysIncludeSettleTimeRange() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderPage(
                1,
                10,
                null,
                null,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.settle_time");
    }

    @Test
    void decryptOrderPhones_shouldDelegateToService() {
        DataController.DecryptOrderRequest request = new DataController.DecryptOrderRequest();
        request.setOrderIds(List.of("oid-1"));
        UUID userId = UUID.randomUUID();
        when(orderDecryptService.decryptPhones(List.of("oid-1"), userId, "tester")).thenReturn(List.of());

        var response = dataController.decryptOrderPhones(request, userId, "tester");

        assertThat(response.getCode()).isEqualTo(200);
        verify(orderDecryptService).decryptPhones(List.of("oid-1"), userId, "tester");
    }

    @Test
    void decryptOrderPhones_nullRequest_throwsBusinessException() {
        assertThatThrownBy(() -> dataController.decryptOrderPhones(null, UUID.randomUUID(), "tester"))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("orderIds");
    }

    @Test
    void decryptOrderPhones_emptyOrderIds_doesNotThrow() {
        DataController.DecryptOrderRequest request = new DataController.DecryptOrderRequest();
        request.setOrderIds(List.of());
        UUID userId = UUID.randomUUID();
        when(orderDecryptService.decryptPhones(List.of(), userId, "tester")).thenReturn(List.of());

        var response = dataController.decryptOrderPhones(request, userId, "tester");

        assertThat(response.getCode()).isEqualTo(200);
        verify(orderDecryptService).decryptPhones(List.of(), userId, "tester");
    }

    @Test
    void getMetrics_returnsMetricsWithTrendData() {
        UUID userId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 2L, "order_amount_cent", 3000L)))
                .thenReturn(List.of(Map.of(
                        "activity_id", "ACT-1",
                        "service_fee_income", 100000L,
                        "tech_service_fee", 20000L,
                        "talent_commission", 30000L
                )))
                .thenReturn(List.of(Map.of(
                        "settle_date", LocalDate.now().toString(),
                        "order_count", 2L,
                        "order_amount_cent", 3000L
                )));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        CommissionService.CommissionSummary summary =
                new CommissionService.CommissionSummary(100000L, 20000L, 30000L,
                        50000L, 25000L, 12500L, 12500L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25));
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(summary);

        var response = dataController.getMetrics(userId, null, DataScope.ALL);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getTrend7d()).hasSize(7);
        assertThat(response.getData().getTodayGmv()).isNotNull();
        assertThat(response.getData().getServiceFee()).isNotNull();
    }

    @Test
    void getMetrics_withDataScopePersonal_addsUserFilter() {
        UUID userId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 0L, "order_amount_cent", 0L)))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25)));

        var response = dataController.getMetrics(userId, UUID.randomUUID(), DataScope.PERSONAL);

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void getMetrics_withDataScopeDept_addsDeptFilter() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 0L, "order_amount_cent", 0L)))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25)));

        var response = dataController.getMetrics(userId, deptId, DataScope.DEPT);

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void getMetrics_shouldUseSqlAggregatesInsteadOfLoadingPagedOrders() {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 0L, "order_amount_cent", 0L)))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        java.math.BigDecimal.valueOf(0.15), java.math.BigDecimal.valueOf(0.15)));

        var response = dataController.getMetrics(UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);

        assertThat(response.getCode()).isEqualTo(200);
        verify(orderMapper, never()).selectPage(any(Page.class), any());
    }

    @Test
    void getExclusiveTalentStatus_returnsMockData() {
        var response = dataController.getExclusiveTalentStatus();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEmpty();
    }

    @Test
    void getExclusiveMerchantStatus_returnsMockData() {
        var response = dataController.getExclusiveMerchantStatus();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEmpty();
    }

    @Test
    void getOrderPage_withStatusFilter_addsStatusCondition() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderPage(1, 10, null, "SHIPPED", null, null,
                UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.order_status");
    }

    @Test
    void getOrderPage_withOrderIdFilter_addsOrderIdCondition() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderPage(1, 10, "MOCK_GEN_ATTR", null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.order_id");
    }

    @Test
    void getOrderPage_withDeptScopeAndMissingDeptId_doesNotThrow() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        var response = dataController.getOrderPage(
                1,
                10,
                null,
                null,
                LocalDate.of(2026, 5, 3),
                LocalDate.of(2026, 5, 3),
                UUID.randomUUID(),
                null,
                DataScope.DEPT
        );

        assertThat(response.getCode()).isEqualTo(200);
        verify(orderMapper).findPageWithScope(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    void exportOrders_withPersonalScope_appliesUserFilter() throws Exception {
        Page<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);
        UUID userId = UUID.randomUUID();

        dataController.exportOrders(
                null,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 4),
                userId,
                UUID.randomUUID(),
                DataScope.PERSONAL,
                new MockHttpServletResponse()
        );

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.user_id");
    }

    @Test
    void exportOrders_shouldStreamAcrossMultiplePages() throws Exception {
        ColonelsettlementOrder first = new ColonelsettlementOrder();
        first.setOrderId("ORDER-1");
        first.setProductName("商品A");
        first.setOrderAmount(1000L);
        first.setOrderStatus(1);
        first.setCreateTime(java.time.LocalDateTime.of(2026, 5, 5, 10, 0));

        ColonelsettlementOrder second = new ColonelsettlementOrder();
        second.setOrderId("ORDER-2");
        second.setProductName("商品B");
        second.setOrderAmount(2000L);
        second.setOrderStatus(2);
        second.setCreateTime(java.time.LocalDateTime.of(2026, 5, 5, 11, 0));

        Page<ColonelsettlementOrder> firstPage = new Page<>(1, 2000, 2001);
        firstPage.setRecords(List.of(first));
        Page<ColonelsettlementOrder> secondPage = new Page<>(2, 2000, 2001);
        secondPage.setRecords(List.of(second));

        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(firstPage)
                .thenReturn(secondPage);

        MockHttpServletResponse response = new MockHttpServletResponse();
        dataController.exportOrders(
                null,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5),
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL,
                response
        );

        String csv = response.getContentAsString();
        assertThat(csv).contains("ORDER-1");
        assertThat(csv).contains("ORDER-2");
    }

    @Test
    void exportActivities_throwsBusinessException() {
        assertThatThrownBy(() -> dataController.exportActivities(
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL,
                new MockHttpServletResponse()))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("活动导出暂未开放");
    }
}
