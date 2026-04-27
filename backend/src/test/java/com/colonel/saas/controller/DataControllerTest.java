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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    void getOrderPage_shouldAlwaysIncludeCreateTimeRange() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderPage(
                1,
                10,
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
        assertThat(segment).contains("co.create_time");
    }

    @Test
    void decryptOrderPhones_shouldDelegateToService() {
        DataController.DecryptOrderRequest request = new DataController.DecryptOrderRequest();
        request.setOrderIds(List.of("oid-1"));
        when(orderDecryptService.decryptPhones(List.of("oid-1"))).thenReturn(List.of());

        var response = dataController.decryptOrderPhones(request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(orderDecryptService).decryptPhones(List.of("oid-1"));
    }

    @Test
    void decryptOrderPhones_nullRequest_throwsBusinessException() {
        assertThatThrownBy(() -> dataController.decryptOrderPhones(null))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("orderIds");
    }

    @Test
    void decryptOrderPhones_emptyOrderIds_doesNotThrow() {
        DataController.DecryptOrderRequest request = new DataController.DecryptOrderRequest();
        request.setOrderIds(List.of());
        when(orderDecryptService.decryptPhones(List.of())).thenReturn(List.of());

        var response = dataController.decryptOrderPhones(request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(orderDecryptService).decryptPhones(List.of());
    }

    @Test
    void getMetrics_returnsMetricsWithTrendData() {
        UUID userId = UUID.randomUUID();
        when(orderMapper.selectList(any())).thenReturn(List.of());
        CommissionService.CommissionSummary summary =
                new CommissionService.CommissionSummary(100000L, 20000L, 30000L,
                        50000L, 25000L, 12500L, 12500L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25));
        when(commissionService.calculate(any())).thenReturn(summary);

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
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(commissionService.calculate(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25)));

        var response = dataController.getMetrics(userId, UUID.randomUUID(), DataScope.PERSONAL);

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void getMetrics_withDataScopeDept_addsDeptFilter() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(commissionService.calculate(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25)));

        var response = dataController.getMetrics(userId, deptId, DataScope.DEPT);

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void getExclusiveTalentStatus_returnsMockData() {
        var response = dataController.getExclusiveTalentStatus();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotEmpty();
        assertThat(response.getData().get(0).get("talentName")).isEqualTo("达人A-独家合作演示");
    }

    @Test
    void getExclusiveMerchantStatus_returnsMockData() {
        var response = dataController.getExclusiveMerchantStatus();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotEmpty();
        assertThat(response.getData().get(0).get("merchantName")).isEqualTo("商家A-独家合作演示");
    }

    @Test
    void getOrderPage_withStatusFilter_addsStatusCondition() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderPage(1, 10, "SHIPPED", null, null,
                UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.order_status");
    }
}
