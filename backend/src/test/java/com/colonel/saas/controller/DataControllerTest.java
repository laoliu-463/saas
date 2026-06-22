package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.performance.facade.OrderPerformanceQueryFacade;
import com.colonel.saas.dto.performance.OrderPerformanceBatchResponse;
import com.colonel.saas.dto.performance.OrderPerformanceDTO;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.vo.data.OrderDetailVO;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.PerformanceMetricsQueryService;
import com.colonel.saas.service.ShortTtlCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class DataControllerTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private CommissionService commissionService;
    @Mock
    private ExclusiveTalentMapper exclusiveTalentMapper;
    @Mock
    private ExclusiveMerchantMapper exclusiveMerchantMapper;
    @Mock
    private ColonelsettlementActivityMapper activityMapper;
    @Mock
    private PerformanceMetricsQueryService performanceMetricsQueryService;
    @Mock
    private OrderPerformanceQueryFacade orderPerformanceQueryFacade;
    @Mock
    private UserDomainFacade userDomainFacade;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private DataController dataController;
    private DataScopePolicy dataScopePolicy;
    private DddRefactorProperties dddRefactorProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> queryWrapperCaptor() {
        return (ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(QueryWrapper.class);
    }

    @BeforeEach
    void setUp() {
        dataScopePolicy = spy(new DataScopePolicy());
        dddRefactorProperties = new DddRefactorProperties();
        dataController = new DataController(
                orderMapper,
                commissionService,
                exclusiveTalentMapper,
                exclusiveMerchantMapper,
                activityMapper,
                new ShortTtlCacheService(),
                performanceMetricsQueryService,
                orderPerformanceQueryFacade,
                userDomainFacade,
                dataScopePolicy,
                dddRefactorProperties,
                jdbcTemplate
        );
        org.mockito.Mockito.lenient().when(performanceMetricsQueryService.hasPerformanceRecords()).thenReturn(false);
        org.mockito.Mockito.lenient()
                .when(performanceMetricsQueryService.resolveAmountTrackLabel(org.mockito.ArgumentMatchers.any()))
                .thenReturn("estimate");
    }

    @Test
    void getOrderPage_shouldUseCreateTimeRangeByDefault() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderPage(
                1,
                10,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.create_time");
        assertThat(segment).doesNotContain("co.settle_time");
    }

    @Test
    void getOrderPage_withSettleTimeField_shouldUseSettleTimeRange() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderPage(
                1,
                10,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "settleTime",
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.settle_time");
        assertThat(segment).doesNotContain("co.create_time");
    }

    @Test
    void getOrderPage_shouldReturnSettleTimeForFrontendDisplay() {
        LocalDateTime createTime = LocalDateTime.of(2026, 5, 10, 9, 30);
        LocalDateTime settleTime = LocalDateTime.of(2026, 5, 10, 15, 45);
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-SETTLE-1");
        order.setProductName("真实结算商品");
        order.setOrderAmount(1000L);
        order.setOrderStatus(1);
        order.setCreateTime(createTime);
        order.setSettleTime(settleTime);
        Page<ColonelsettlementOrder> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(order));
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        var response = dataController.getOrderPage(
                1,
                10,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 5, 10),
                "settleTime",
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        assertThat(response.getData().getRecords()).hasSize(1);
        var vo = response.getData().getRecords().get(0);
        assertThat(vo.getCreateTime()).isEqualTo(createTime);
        assertThat(vo.getSettleTime()).isEqualTo(settleTime);
    }

    @Test
    void getMetrics_withPerformanceRecords_shouldUsePerformanceAggregate() {
        when(performanceMetricsQueryService.hasPerformanceRecords()).thenReturn(true);
        when(performanceMetricsQueryService.resolveAmountTrackLabel("createTime")).thenReturn("estimate");
        when(performanceMetricsQueryService.aggregateRange(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PerformanceMetricsQueryService.PerformanceAggregate(
                        3L, 9000L, 900L, 90L, 0L, 50L, 810L, 81L, 162L, 567L));
        when(performanceMetricsQueryService.trendByDay(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new PerformanceMetricsQueryService.TrendPoint(LocalDate.now().toString(), 3L, 9000L)));
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 1L)));

        var response = dataController.getMetrics(UUID.randomUUID(), null, DataScope.ALL);

        assertThat(response.getData().getEstimate().getMetricsSource()).isEqualTo("performance_records");
        assertThat(response.getData().getEstimate().getAmountTrack()).isEqualTo("estimate");
        assertThat(response.getData().getEstimate().getTalentCommission()).isEqualByComparingTo("0.50");
        assertThat(response.getData().getEstimate().getGrossProfit()).isEqualByComparingTo("5.67");
        verify(commissionService, never()).calculateByActivityBuckets(any());
    }

    @Test
    void getMetrics_withPerformanceRecords_shouldUseTrackSpecificProfitFormula() {
        when(performanceMetricsQueryService.hasPerformanceRecords()).thenReturn(true);
        when(performanceMetricsQueryService.aggregateRange(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String timeField = invocation.getArgument(2);
                    if ("settleTime".equals(timeField)) {
                        return new PerformanceMetricsQueryService.PerformanceAggregate(
                                1L, 10000L, 1000L, 100L, 10L, 0L, 990L, 100L, 150L, 740L);
                    }
                    return new PerformanceMetricsQueryService.PerformanceAggregate(
                            1L, 10000L, 1000L, 100L, 10L, 0L, 890L, 100L, 150L, 640L);
                });
        when(performanceMetricsQueryService.trendByDay(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new PerformanceMetricsQueryService.TrendPoint(LocalDate.now().toString(), 1L, 10000L)));
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 0L)));

        var response = dataController.getMetrics(UUID.randomUUID(), null, DataScope.ALL);

        assertThat(response.getData().getSettle().getServiceFeeProfit()).isEqualByComparingTo("9.90");
        assertThat(response.getData().getSettle().getGrossProfit()).isEqualByComparingTo("7.40");
        assertThat(response.getData().getEstimate().getServiceFeeProfit()).isEqualByComparingTo("8.90");
        assertThat(response.getData().getEstimate().getGrossProfit()).isEqualByComparingTo("6.40");
        verify(commissionService, never()).calculateByActivityBuckets(any());
    }

    @Test
    void getMetrics_returnsMetricsWithTrendData() {
        UUID userId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 0L)))
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
                )))
                .thenReturn(List.of(Map.of("order_count", 0L)))
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
        CommissionService.CommissionSummary summary =
                new CommissionService.CommissionSummary(100000L, 20000L, 0L, 30000L,
                        50000L, 25000L, 12500L, 12500L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25));
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(summary);

        var response = dataController.getMetrics(userId, null, DataScope.ALL);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getEstimate().getTrend7d()).hasSize(7);
        assertThat(response.getData().getEstimate().getTodayGmv()).isNotNull();
        assertThat(response.getData().getEstimate().getServiceFee()).isNotNull();
        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper, times(8)).selectMaps(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues().get(1).getSqlSelect()).contains("settle_amount");
        assertThat(wrapperCaptor.getAllValues().get(2).getSqlSelect())
                .contains("effective_service_fee")
                .contains("effective_tech_service_fee");
        assertThat(wrapperCaptor.getAllValues().get(3).getSqlSelect()).contains("settle_amount");
        assertThat(wrapperCaptor.getAllValues().get(4).getSqlSegment()).contains("create_time");
        assertThat(wrapperCaptor.getAllValues().get(5).getSqlSelect()).contains("order_amount");
        assertThat(wrapperCaptor.getAllValues().get(6).getSqlSelect())
                .contains("estimate_service_fee")
                .contains("estimate_tech_service_fee");
        assertThat(wrapperCaptor.getAllValues().get(7).getSqlSelect()).contains("order_amount");
    }

    @Test
    void getMetrics_withAllScope_shouldShareCacheAcrossUsers() {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 0L)))
                .thenReturn(List.of(Map.of("order_count", 0L, "order_amount_cent", 0L)))
                .thenReturn(List.of(Map.of("order_count", 0L)))
                .thenReturn(List.of())
                .thenReturn(List.of(Map.of("order_count", 0L)))
                .thenReturn(List.of(Map.of("order_count", 0L, "order_amount_cent", 0L)))
                .thenReturn(List.of(Map.of("order_count", 0L)))
                .thenReturn(List.of());
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        java.math.BigDecimal.valueOf(0.15), java.math.BigDecimal.valueOf(0.15)));

        var first = dataController.getMetrics(firstUser, UUID.randomUUID(), DataScope.ALL);
        var second = dataController.getMetrics(secondUser, UUID.randomUUID(), DataScope.ALL);

        assertThat(first.getCode()).isEqualTo(200);
        assertThat(second.getCode()).isEqualTo(200);
        verify(orderMapper, times(8)).selectMaps(any(QueryWrapper.class));
        verify(commissionService, times(2)).calculateByActivityBuckets(any());
    }

    @Test
    void getMetrics_withDataScopePersonal_addsUserFilter() {
        UUID userId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 0L, "order_amount_cent", 0L)))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25)));

        var response = dataController.getMetrics(userId, UUID.randomUUID(), DataScope.PERSONAL);

        assertThat(response.getCode()).isEqualTo(200);
        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper, times(8)).selectMaps(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues())
                .allSatisfy(wrapper -> assertThat(wrapper.getSqlSegment()).contains("user_id"));
        verify(dataScopePolicy, never()).contextRequirement(any(), any(), any());
        verify(dataScopePolicy, never()).applyTo(any(QueryWrapper.class), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void getMetrics_withDataScopeDept_addsDeptFilter() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 0L, "order_amount_cent", 0L)))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25)));

        var response = dataController.getMetrics(userId, deptId, DataScope.DEPT);

        assertThat(response.getCode()).isEqualTo(200);
        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper, times(8)).selectMaps(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues())
                .allSatisfy(wrapper -> assertThat(wrapper.getSqlSegment()).contains("dept_id"));
        verify(dataScopePolicy, never()).contextRequirement(any(), any(), any());
        verify(dataScopePolicy, never()).applyTo(any(QueryWrapper.class), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void getMetrics_withDataScopePolicyEnabled_delegatesToUserPolicy() {
        dddRefactorProperties.getDataScopePolicy().setEnabled(true);
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 0L, "order_amount_cent", 0L)))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25)));

        var response = dataController.getMetrics(userId, deptId, DataScope.DEPT);

        assertThat(response.getCode()).isEqualTo(200);
        verify(dataScopePolicy, times(8)).contextRequirement(userId, deptId, DataScope.DEPT);
        verify(dataScopePolicy, times(8)).applyTo(
                any(QueryWrapper.class), eq(userId), eq(deptId), eq(DataScope.DEPT), eq("user_id"), eq("dept_id"));
    }

    @Test
    void getMetrics_withPersonalScopeAndMissingUser_shouldFailClosed() {
        assertThatThrownBy(() -> dataController.getMetrics(null, UUID.randomUUID(), DataScope.PERSONAL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少用户上下文");

        verify(orderMapper, never()).selectMaps(any(QueryWrapper.class));
        verify(dataScopePolicy, never()).contextRequirement(any(), any(), any());
        verify(dataScopePolicy, never()).applyTo(any(QueryWrapper.class), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void getMetrics_withDeptScopeAndMissingDept_shouldFailClosed() {
        assertThatThrownBy(() -> dataController.getMetrics(UUID.randomUUID(), null, DataScope.DEPT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少部门上下文");

        verify(orderMapper, never()).selectMaps(any(QueryWrapper.class));
        verify(dataScopePolicy, never()).contextRequirement(any(), any(), any());
        verify(dataScopePolicy, never()).applyTo(any(QueryWrapper.class), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void getMetrics_shouldUseSqlAggregatesInsteadOfLoadingPagedOrders() {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 0L, "order_amount_cent", 0L)))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        java.math.BigDecimal.valueOf(0.15), java.math.BigDecimal.valueOf(0.15)));

        var response = dataController.getMetrics(UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);

        assertThat(response.getCode()).isEqualTo(200);
        verify(orderMapper, never()).selectPage(any(Page.class), any());
    }

    @Test
    void getMetrics_withNoScopeAndSettleAlias_handlesEmptyAndInvalidAggregates() {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(List.of(Map.of("order_count", "not-a-number")))
                .thenReturn(List.of(Map.of(
                        "ACTIVITY_ID", "ACT-1",
                        "service_fee_income", "bad-number",
                        "tech_service_fee", 100L,
                        "talent_commission", 200L
                )))
                .thenReturn(List.of(Map.of(
                        "SETTLE_DATE", LocalDate.now().toString(),
                        "ORDER_COUNT", "bad-number",
                        "ORDER_AMOUNT_CENT", 100L
                )))
                .thenReturn(null)
                .thenReturn(List.of(Map.of("order_count", "not-a-number")))
                .thenReturn(List.of(Map.of(
                        "ACTIVITY_ID", "ACT-1",
                        "service_fee_income", "bad-number",
                        "tech_service_fee", 100L,
                        "talent_commission", 200L
                )))
                .thenReturn(List.of(Map.of(
                        "SETTLE_DATE", LocalDate.now().toString(),
                        "ORDER_COUNT", "bad-number",
                        "ORDER_AMOUNT_CENT", 100L
                )));
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 100L, 0L, 200L, 300L, 150L, 75L, 75L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25)));

        var response = dataController.getMetrics(UUID.randomUUID(), null, null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getSettle().getTodayOrderCount()).isZero();
        assertThat(response.getData().getSettle().getPendingShipCount()).isZero();
        assertThat(response.getData().getSettle().getTrend7d().get(6).getOrderCount()).isZero();
        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper, times(8)).selectMaps(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues().get(0).getSqlSegment()).contains("settle_time");
    }

    @Test
    void getExclusiveTalentStatus_returnsPagedData() {
        Page<ExclusiveTalent> page = new Page<>(1, 10, 1);
        ExclusiveTalent record = new ExclusiveTalent();
        record.setTalentUid("talent-001");
        page.setRecords(List.of(record));
        when(exclusiveTalentMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var response = dataController.getExclusiveTalentStatus(
                1,
                10,
                "2026-05",
                "talent",
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getTalentUid()).isEqualTo("talent-001");
    }

    @Test
    void getExclusiveTalentStatus_withPersonalScope_addsUserFilter() {
        Page<ExclusiveTalent> page = new Page<>(1, 10, 0);
        when(exclusiveTalentMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var response = dataController.getExclusiveTalentStatus(
                1,
                10,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                DataScope.PERSONAL
        );

        assertThat(response.getCode()).isEqualTo(200);
        verify(exclusiveTalentMapper).selectPage(any(Page.class), any());
    }

    @Test
    void getExclusiveTalentStatus_shouldReturnVoWithoutEntityAuditFields() {
        Page<ExclusiveTalent> page = new Page<>(1, 10, 1);
        ExclusiveTalent record = new ExclusiveTalent();
        record.setTalentUid("talent-001");
        page.setRecords(List.of(record));
        when(exclusiveTalentMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var response = dataController.getExclusiveTalentStatus(
                1,
                10,
                null,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        Object firstRecord = response.getData().getRecords().get(0);
        Map<String, Object> serialized = objectMapper.convertValue(firstRecord, new TypeReference<>() {
        });

        assertThat(firstRecord).isNotInstanceOf(ExclusiveTalent.class);
        assertThat(serialized).containsEntry("talentUid", "talent-001");
        assertThat(serialized).doesNotContainKeys("createBy", "updateBy", "deleted", "deptId");
    }

    @Test
    void getExclusiveMerchantStatus_returnsPagedData() {
        Page<ExclusiveMerchant> page = new Page<>(1, 10, 1);
        ExclusiveMerchant record = new ExclusiveMerchant();
        record.setMerchantName("商家A");
        page.setRecords(List.of(record));
        when(exclusiveMerchantMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var response = dataController.getExclusiveMerchantStatus(
                1,
                10,
                "2026-05",
                "商家",
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getMerchantName()).isEqualTo("商家A");
    }

    @Test
    void getExclusiveMerchantStatus_withDeptScope_addsDeptFilter() {
        Page<ExclusiveMerchant> page = new Page<>(1, 10, 0);
        when(exclusiveMerchantMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var response = dataController.getExclusiveMerchantStatus(
                1,
                10,
                null,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.DEPT
        );

        assertThat(response.getCode()).isEqualTo(200);
        verify(exclusiveMerchantMapper).selectPage(any(Page.class), any());
    }

    @Test
    void getExclusiveMerchantStatus_shouldReturnVoWithoutEntityAuditFields() {
        Page<ExclusiveMerchant> page = new Page<>(1, 10, 1);
        ExclusiveMerchant record = new ExclusiveMerchant();
        record.setMerchantName("商家A");
        page.setRecords(List.of(record));
        when(exclusiveMerchantMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var response = dataController.getExclusiveMerchantStatus(
                1,
                10,
                null,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        Object firstRecord = response.getData().getRecords().get(0);
        Map<String, Object> serialized = objectMapper.convertValue(firstRecord, new TypeReference<>() {
        });

        assertThat(firstRecord).isNotInstanceOf(ExclusiveMerchant.class);
        assertThat(serialized).containsEntry("merchantName", "商家A");
        assertThat(serialized).doesNotContainKeys("createBy", "updateBy", "deleted", "deptId");
    }

    @Test
    void getOrderPage_withStatusFilter_addsStatusCondition() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderPage(1, 10, null, "SHIPPED", null, null, null, null, null, null,
                null, null, null,
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

        dataController.getOrderPage(1, 10, "MOCK_GEN_ATTR", null, null, null, null, null, null, null,
                null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.order_id");
    }

    @Test
    void getOrderPage_withDeptScopeAndMissingDeptId_shouldFailClosed() {
        assertThatThrownBy(() -> dataController.getOrderPage(
                1,
                10,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 3),
                LocalDate.of(2026, 5, 3),
                null,
                UUID.randomUUID(),
                null,
                DataScope.DEPT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少部门上下文");

        verify(orderMapper, never()).findPageWithScope(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    void getOrderPage_withTalentAndMerchantFilter_addsConditions() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);
        UUID talentId = UUID.randomUUID();

        dataController.getOrderPage(
                1,
                10,
                null,
                null,
                talentId,
                "merchant_10086",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.talent_id");
        assertThat(segment).contains("merchant_id");
    }

    @Test
    void getOrderSummary_returnsTotalsAndDailyRowsWithSupportedFilters() {
        UUID recruiterId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of(
                        "order_count", 2L,
                        "talent_promoter_count", 1L,
                        "colonel_promoter_count", 1L,
                        "product_count", 1L,
                        "order_amount_cent", 10000L,
                        "actual_amount_cent", 8000L,
                        "service_fee_income_cent", 500L,
                        "tech_service_fee_cent", 100L,
                        "talent_commission_cent", 0L
                )))
                .thenReturn(List.of(Map.of(
                        "stat_date", "2026-05-25",
                        "order_count", 2L,
                        "talent_promoter_count", 1L,
                        "colonel_promoter_count", 1L,
                        "product_count", 1L,
                        "order_amount_cent", 10000L,
                        "actual_amount_cent", 8000L,
                        "service_fee_income_cent", 500L,
                        "tech_service_fee_cent", 100L,
                        "talent_commission_cent", 0L
                )))
                .thenReturn(List.of(Map.of(
                        "activity_id", "ACT-1",
                        "product_id", "P-1",
                        "recruiter_user_id", recruiterId.toString(),
                        "service_fee_income", 500L,
                        "tech_service_fee", 100L,
                        "talent_commission", 0L
                )))
                .thenReturn(List.of(Map.of(
                        "stat_date", "2026-05-25",
                        "activity_id", "ACT-1",
                        "product_id", "P-1",
                        "recruiter_user_id", recruiterId.toString(),
                        "service_fee_income", 500L,
                        "tech_service_fee", 100L,
                        "talent_commission", 0L
                )));
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(500L, 100L, 0L, 0L, 400L, 40L, 60L, 300L,
                        java.math.BigDecimal.valueOf(0.1), java.math.BigDecimal.valueOf(0.15)));

        var response = dataController.getOrderSummary(
                "ORDER-1",
                "FINISHED",
                null,
                "merchant_1",
                "P-1",
                "商品",
                "店铺",
                "达人",
                "招商",
                "渠道",
                "ACT-1",
                "PROMOTION",
                LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 5, 31),
                "createTime",
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTotal().getOrderCount()).isEqualTo(2L);
        assertThat(response.getData().getTotal().getOrderAmount()).isEqualByComparingTo("100.00");
        assertThat(response.getData().getTotal().getProductAverageServiceFeeRate()).isEqualByComparingTo("6.25");
        assertThat(response.getData().getTotal().getOrderAverageServiceFeeRate()).isEqualByComparingTo("4.00");
        assertThat(response.getData().getTotal().getServiceFeeExpense()).isEqualByComparingTo("0.00");
        assertThat(response.getData().getTotal().getServiceFeeProfit()).isEqualByComparingTo("4.00");
        assertThat(response.getData().getTotal().getGrossProfit()).isEqualByComparingTo("3.00");
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getDate()).isEqualTo("2026-05-25");

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper, times(4)).selectMaps(wrapperCaptor.capture());
        String segment = wrapperCaptor.getAllValues().get(0).getSqlSegment();
        assertThat(segment).contains("product_id");
        assertThat(segment).contains("product_name");
        assertThat(segment).contains("shop_name");
        assertThat(segment).contains("talent_name");
        assertThat(segment).contains("colonel_user_name");
        assertThat(segment).contains("colonel_activity_id");
        assertThat(segment).contains("order_type");
    }

    @Test
    void getOrderSummary_withEmptyResult_returnsZeroSummary() {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of());

        var response = dataController.getOrderSummary(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 5, 31),
                "createTime",
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTotal().getOrderCount()).isZero();
        assertThat(response.getData().getTotal().getOrderAmount()).isEqualByComparingTo("0.00");
        assertThat(response.getData().getTotal().getProductAverageServiceFeeRate()).isEqualByComparingTo("0.00");
        assertThat(response.getData().getTotal().getServiceFeeIncome()).isEqualByComparingTo("0.00");
        assertThat(response.getData().getRecords()).isEmpty();
        verify(orderMapper, times(4)).selectMaps(any(QueryWrapper.class));
    }

    @Test
    void getOrderSummary_shouldExcludeDeletedOrdersLikeOrderPage() {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of());

        dataController.getOrderSummary(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 5, 31),
                "createTime",
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper, times(4)).selectMaps(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues())
                .allSatisfy(wrapper -> assertThat(wrapper.getSqlSegment()).contains("deleted"));
    }

    @Test
    void getOrderSummary_withSettleTimeField_usesEffectiveTrackColumns() {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of(
                        "order_count", 1L,
                        "talent_promoter_count", 1L,
                        "colonel_promoter_count", 0L,
                        "product_count", 1L,
                        "order_amount_cent", 9000L,
                        "actual_amount_cent", 10000L,
                        "service_fee_income_cent", 450L,
                        "tech_service_fee_cent", 50L,
                        "service_fee_expense_cent", 20L,
                        "talent_commission_cent", 0L
                )))
                .thenReturn(List.of())
                .thenReturn(List.of(Map.of(
                        "activity_id", "ACT-1",
                        "product_id", "P-1",
                        "service_fee_income", 450L,
                        "tech_service_fee", 50L,
                        "service_fee_expense", 20L,
                        "talent_commission", 0L
                )))
                .thenReturn(List.of());
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(450L, 0L, 20L, 0L, 430L, 43L, 64L, 323L,
                        java.math.BigDecimal.valueOf(0.1), java.math.BigDecimal.valueOf(0.1)));

        var response = dataController.getOrderSummary(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 5, 31),
                "settleTime",
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTotal().getOrderAmount()).isEqualByComparingTo("90.00");
        assertThat(response.getData().getTotal().getOrderAverageServiceFeeRate()).isEqualByComparingTo("4.78");
        assertThat(response.getData().getTotal().getServiceFeeExpense()).isEqualByComparingTo("0.20");
        assertThat(response.getData().getTotal().getServiceFeeProfit()).isEqualByComparingTo("4.30");
        assertThat(response.getData().getTotal().getGrossProfit()).isEqualByComparingTo("3.23");

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper, times(4)).selectMaps(wrapperCaptor.capture());
        QueryWrapper<ColonelsettlementOrder> aggregateWrapper = wrapperCaptor.getAllValues().get(0);
        assertThat(aggregateWrapper.getSqlSegment()).contains("settle_time");
        assertThat(aggregateWrapper.getSqlSelect()).contains("settle_amount");
        assertThat(aggregateWrapper.getSqlSelect()).contains("effective_service_fee");
        assertThat(aggregateWrapper.getSqlSelect()).contains("effective_tech_service_fee");
        assertThat(aggregateWrapper.getSqlSelect()).contains("effective_service_fee_expense");

        ArgumentCaptor<List<CommissionService.ActivityCommissionBucket>> bucketCaptor = ArgumentCaptor.forClass(List.class);
        verify(commissionService).calculateByActivityBuckets(bucketCaptor.capture());
        assertThat(bucketCaptor.getAllValues().get(0)).singleElement().satisfies(bucket -> {
            assertThat(bucket.techServiceFee()).isZero();
            assertThat(bucket.serviceFeeExpense()).isEqualTo(20L);
        });
    }

    @Test
    void getOrderSummary_withInvalidTimeField_shouldThrowBusinessException() {
        assertThatThrownBy(() -> dataController.getOrderSummary(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 5, 31),
                "paidAt",
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法时间字段");
        verify(orderMapper, never()).selectMaps(any());
    }

    @Test
    void exportOrders_withPersonalScope_appliesUserFilter() throws Exception {
        Page<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);
        UUID userId = UUID.randomUUID();

        dataController.exportOrders(
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 4),
                null,
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
    void exportOrders_withOrderIdFilter_appliesSameFilterAsPage() throws Exception {
        Page<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.exportOrders(
                "ORDER-7788",
                null,
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL,
                new MockHttpServletResponse()
        );

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.order_id");
    }

    @Test
    void exportOrders_withFiltersAndEscaping_shouldWriteDefaultAttribution() throws Exception {
        UUID orderPk = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(orderPk);
        order.setProductName("商品,\"A\"");
        order.setOrderAmount(1234L);
        order.setOrderStatus(4);
        order.setCreateTime(LocalDateTime.of(2026, 5, 5, 10, 0));
        order.setSettleTime(LocalDateTime.of(2026, 5, 5, 16, 30));

        Page<ColonelsettlementOrder> page = new Page<>(1, 2000, 1);
        page.setRecords(List.of(order));
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        MockHttpServletResponse response = new MockHttpServletResponse();
        dataController.exportOrders(
                null,
                "CANCELLED",
                talentId,
                "merchant_10086",
                null,
                null,
                "settle",
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL,
                response
        );

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.settle_time", "co.order_status", "co.talent_id", "merchant_id");
        String csv = response.getContentAsString();
        assertThat(csv).contains(orderPk.toString());
        assertThat(csv).contains("\"商品,\"\"A\"\"\"");
        assertThat(csv).contains("默认归属");
        assertThat(csv).contains("CANCELLED");
    }

    @Test
    void exportOrders_withInvalidStatus_shouldThrowBusinessException() {
        assertThatThrownBy(() -> dataController.exportOrders(
                null,
                "UNKNOWN",
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL,
                new MockHttpServletResponse()
        )).hasMessageContaining("非法订单状态");

        verify(orderMapper, never()).findPageWithScope(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    void exportOrders_shouldStreamAcrossMultiplePages() throws Exception {
        ColonelsettlementOrder first = new ColonelsettlementOrder();
        first.setOrderId("ORDER-1");
        first.setProductName("商品A");
        first.setOrderAmount(1000L);
        first.setOrderStatus(1);
        first.setCreateTime(LocalDateTime.of(2026, 5, 5, 10, 0));
        first.setSettleTime(LocalDateTime.of(2026, 5, 5, 16, 30));

        ColonelsettlementOrder second = new ColonelsettlementOrder();
        second.setOrderId("ORDER-2");
        second.setProductName("商品B");
        second.setOrderAmount(2000L);
        second.setOrderStatus(2);
        second.setCreateTime(LocalDateTime.of(2026, 5, 5, 11, 0));
        second.setSettleTime(LocalDateTime.of(2026, 5, 5, 17, 0));

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
                null,
                null,
                null,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL,
                response
        );

        String csv = response.getContentAsString();
        assertThat(csv).contains("创建时间,结算时间");
        assertThat(csv).contains("ORDER-1");
        assertThat(csv).contains("ORDER-2");
        assertThat(csv).contains("2026-05-05T16:30");
    }

    @Test
    void exportActivities_shouldWriteCsv() throws Exception {
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId("ACT-001");
        activity.setName("活动A");
        activity.setStartTime(java.time.LocalDateTime.of(2026, 5, 1, 10, 0));
        activity.setEndTime(java.time.LocalDateTime.of(2026, 5, 31, 23, 59));
        activity.setStatus(1);
        when(activityMapper.selectExportPage(any(Long.class), any(Long.class), any(), any())).thenReturn(List.of(activity));

        MockHttpServletResponse response = new MockHttpServletResponse();
        dataController.exportActivities(
                "活动",
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL,
                response
        );

        String csv = response.getContentAsString();
        assertThat(csv).contains("活动ID,活动名称,开始时间,结束时间,状态");
        assertThat(csv).contains("ACT-001");
        assertThat(csv).contains("活动A");
    }

    @Test
    void exportActivities_shouldStreamMultiplePagesAndEndedStatus() throws Exception {
        ColonelsettlementActivity ongoing = new ColonelsettlementActivity();
        ongoing.setActivityId("ACT-ONGOING");
        ongoing.setName("活动A");
        ongoing.setStartTime(LocalDateTime.of(2026, 5, 1, 10, 0));
        ongoing.setEndTime(LocalDateTime.of(2026, 5, 31, 23, 59));
        ongoing.setStatus(1);
        List<ColonelsettlementActivity> fullPage = java.util.Collections.nCopies(2000, ongoing);

        ColonelsettlementActivity ended = new ColonelsettlementActivity();
        ended.setActivityId("ACT-END");
        ended.setName("活动,\"B\"");
        ended.setStartTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        ended.setEndTime(LocalDateTime.of(2026, 4, 30, 23, 59));
        ended.setStatus(0);

        when(activityMapper.selectExportPage(any(Long.class), any(Long.class), any(), any()))
                .thenReturn(fullPage)
                .thenReturn(List.of(ended));

        MockHttpServletResponse response = new MockHttpServletResponse();
        dataController.exportActivities(
                " ",
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL,
                response
        );

        verify(activityMapper).selectExportPage(org.mockito.ArgumentMatchers.eq(0L), any(Long.class), org.mockito.ArgumentMatchers.isNull(), any());
        verify(activityMapper).selectExportPage(org.mockito.ArgumentMatchers.eq(2000L), any(Long.class), org.mockito.ArgumentMatchers.isNull(), any());
        String csv = response.getContentAsString();
        assertThat(csv).contains("ACT-END");
        assertThat(csv).contains("\"活动,\"\"B\"\"\"");
        assertThat(csv).contains("已结束");
    }

    @Test
    void sensitiveDataEndpoints_shouldRequireAdminOrLeaderRoles() throws Exception {
        Method exportOrders = DataController.class.getDeclaredMethod(
                "exportOrders",
                String.class,
                String.class,
                UUID.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                String.class,
                UUID.class,
                UUID.class,
                DataScope.class,
                jakarta.servlet.http.HttpServletResponse.class
        );
        Method getExclusiveTalentStatus = DataController.class.getDeclaredMethod(
                "getExclusiveTalentStatus",
                long.class,
                long.class,
                String.class,
                String.class,
                Integer.class,
                UUID.class,
                UUID.class,
                DataScope.class
        );
        Method getExclusiveMerchantStatus = DataController.class.getDeclaredMethod(
                "getExclusiveMerchantStatus",
                long.class,
                long.class,
                String.class,
                String.class,
                Integer.class,
                UUID.class,
                UUID.class,
                DataScope.class
        );
        Method exportActivities = DataController.class.getDeclaredMethod(
                "exportActivities",
                String.class,
                UUID.class,
                UUID.class,
                DataScope.class,
                jakarta.servlet.http.HttpServletResponse.class
        );

        assertThat(exportOrders.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(exportOrders.getAnnotation(RequireRoles.class).value())
                .containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER);
        assertThat(getExclusiveTalentStatus.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(getExclusiveTalentStatus.getAnnotation(RequireRoles.class).value())
                .containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER);
        assertThat(getExclusiveMerchantStatus.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(getExclusiveMerchantStatus.getAnnotation(RequireRoles.class).value())
                .containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER);
        assertThat(exportActivities.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(exportActivities.getAnnotation(RequireRoles.class).value())
                .containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER);
    }

    @Test
    void getOrderDetailPage_shouldReturnEmptyWhenNoOrders() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 20);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        var result = dataController.getOrderDetailPage(
                1, 20, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, UUID.randomUUID(), null, DataScope.ALL);

        assertThat(result).isNotNull();
        assertThat(result.getData().getRecords()).isEmpty();
    }

    @Test
    void getOrderDetailPage_shouldApplyDetailSpecificFilters() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 20);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderDetailPage(
                1, 20, null, null, null, "legacy-partner-ignored",
                null, null, null, null, "旧招商字段", "渠道甲", "ACT-1",
                "活动甲", "PARTNER-1", "合作方甲", "招商甲",
                null, null, null, "createTime", null, null,
                UUID.randomUUID(), null, DataScope.ALL);

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("merchant_id");
        assertThat(segment).contains("colonel_activity");
        assertThat(segment).contains("activity_name");
        assertThat(segment).contains("partner_name");
        assertThat(segment).contains("final_channel_user_id");
        assertThat(segment).contains("final_recruiter_user_id");
        assertThat(segment).doesNotContain("media");
    }

    @Test
    void getOrderDetailPage_shouldMergePerformanceData() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD001");
        order.setOrderStatus(3);
        order.setOrderAmount(19900L);
        order.setSettleAmount(18900L);
        order.setEstimateServiceFee(1000L);
        order.setEffectiveServiceFee(950L);
        order.setEstimateTechServiceFee(500L);
        order.setEffectiveTechServiceFee(480L);
        order.setProductTitle("Test Product");
        order.setProductId("P001");
        order.setShopName("Test Shop");
        order.setColonelUserName("ColonelWang");
        order.setOrderType(1);
        order.setTalentName("TestTalent");
        order.setActivityId("ACT001");
        order.setOrderCreateTime(LocalDateTime.of(2026, 6, 4, 13, 50));
        order.setPayTime(LocalDateTime.of(2026, 6, 4, 13, 57, 32));
        order.setExtraData(Map.of(
                "delivery_time", "2026-06-05 10:00:00",
                "expire_time", "2026-07-04 00:00:00"
        ));

        Page<ColonelsettlementOrder> orderPage = new Page<>(1, 20);
        orderPage.setRecords(List.of(order));
        orderPage.setTotal(1);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(orderPage);

        OrderPerformanceDTO perf = new OrderPerformanceDTO();
        perf.setOrderId("ORD001");
        UUID channelUserId = UUID.randomUUID();
        UUID recruiterUserId = UUID.randomUUID();
        perf.setFinalChannelId(channelUserId.toString());
        perf.setFinalRecruiterId(recruiterUserId.toString());
        perf.setEstimateRecruiterCommission(400L);
        perf.setEffectiveRecruiterCommission(380L);
        perf.setEstimateChannelCommission(300L);
        perf.setEffectiveChannelCommission(285L);
        perf.setEstimateServiceProfit(500L);
        perf.setEffectiveServiceProfit(470L);
        perf.setEstimateGrossProfit(520L);
        perf.setEffectiveGrossProfit(493L);
        perf.setIsValid(Boolean.TRUE);
        OrderPerformanceBatchResponse perfResponse = new OrderPerformanceBatchResponse();
        perfResponse.setItems(List.of(perf));
        when(orderPerformanceQueryFacade.batchGetOrderPerformance(eq(List.of("ORD001")), any())).thenReturn(perfResponse);

        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId("ACT001");
        activity.setName("Test Activity");
        when(activityMapper.selectNamesByActivityIds(any())).thenReturn(List.of(activity));

        when(userDomainFacade.loadUserDisplayNamesByIds(any()))
                .thenReturn(Map.of(channelUserId, "ChannelZhang", recruiterUserId, "RecruiterLi"));

        var result = dataController.getOrderDetailPage(
                1, 20, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, UUID.randomUUID(), null, DataScope.ALL);

        assertThat(result).isNotNull();
        List<OrderDetailVO> records = result.getData().getRecords();
        assertThat(records).hasSize(1);
        OrderDetailVO vo = records.get(0);

        assertThat(vo.getOrderId()).isEqualTo("ORD001");
        assertThat(vo.getActivityName()).isEqualTo("Test Activity");
        assertThat(vo.getOrderTypeText()).isEqualTo("推广者推广");
        assertThat(vo.getColonelName()).isEqualTo("ColonelWang");
        assertThat(vo.getChannelName()).isEqualTo("ChannelZhang");
        assertThat(vo.getRecruiterName()).isEqualTo("RecruiterLi");
        assertThat(vo.getPayTime()).isEqualTo(LocalDateTime.of(2026, 6, 4, 13, 57, 32));
        assertThat(vo.getDeliveryTime()).isEqualTo(LocalDateTime.of(2026, 6, 5, 10, 0));
        assertThat(vo.getExpireTime()).isEqualTo(LocalDateTime.of(2026, 7, 4, 0, 0));

        // 预估服务费支出 = 预估收入 - 技术服务费 - 服务费收益；结算服务费支出 = 结算收入 - 服务费收益
        assertThat(vo.getEstimateServiceFeeExpense()).isNotNull();
        assertThat(vo.getEstimateServiceFeeExpense().doubleValue()).isEqualTo(0.00);
        assertThat(vo.getEffectiveServiceFeeExpense()).isNotNull();
        assertThat(vo.getEffectiveServiceFeeExpense().doubleValue()).isEqualTo(4.80);
        assertThat(vo.getEstimateServiceProfit()).isEqualByComparingTo("5.00");
        assertThat(vo.getEffectiveServiceProfit()).isEqualByComparingTo("4.70");
        assertThat(vo.getEstimateGrossProfit()).isEqualByComparingTo("5.20");
        assertThat(vo.getEffectiveGrossProfit()).isEqualByComparingTo("4.93");
        verify(userDomainFacade, never()).getUsersByIds(any());

        // 双轨金额
        assertThat(vo.getPayAmount()).isNotNull();
        assertThat(vo.getSettleAmount()).isNotNull();
        assertThat(vo.getEstimateServiceFee()).isNotNull();
        assertThat(vo.getEffectiveServiceFee()).isNotNull();
    }

    @Test
    void getOrderDetailPage_shouldHandleOrderWithoutPerformance() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD002");
        order.setOrderStatus(1);
        order.setOrderAmount(5000L);
        order.setEstimateServiceFee(200L);
        order.setEstimateTechServiceFee(100L);
        order.setProductTitle("NoPerf Product");
        order.setOrderCreateTime(LocalDateTime.now());

        Page<ColonelsettlementOrder> orderPage = new Page<>(1, 20);
        orderPage.setRecords(List.of(order));
        orderPage.setTotal(1);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(orderPage);
        OrderPerformanceBatchResponse emptyPerfResponse = new OrderPerformanceBatchResponse();
        emptyPerfResponse.setItems(List.of());
        when(orderPerformanceQueryFacade.batchGetOrderPerformance(eq(List.of("ORD002")), any())).thenReturn(emptyPerfResponse);
        org.mockito.Mockito.lenient().when(activityMapper.selectNamesByActivityIds(any())).thenReturn(List.of());

        var result = dataController.getOrderDetailPage(
                1, 20, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, UUID.randomUUID(), null, DataScope.ALL);

        List<OrderDetailVO> records = result.getData().getRecords();
        assertThat(records).hasSize(1);
        OrderDetailVO vo = records.get(0);

        assertThat(vo.getOrderId()).isEqualTo("ORD002");
        assertThat(vo.getChannelName()).isNull();
        assertThat(vo.getRecruiterName()).isNull();
        assertThat(vo.getEstimateRecruiterCommission()).isNull();
        assertThat(vo.getEstimateChannelCommission()).isNull();
        assertThat(vo.getSettleAmount()).isNull();
        assertThat(vo.getEffectiveServiceFee()).isNull();
        assertThat(vo.getEffectiveTechServiceFee()).isNull();
        assertThat(vo.getEffectiveServiceProfit()).isNull();
        // 无业绩记录时不推导服务费收益/支出
        assertThat(vo.getEstimateServiceProfit()).isNull();
        assertThat(vo.getEstimateServiceFeeExpense()).isNull();
    }

    @Test
    void exportOrderDetail_shouldWriteCsv() throws Exception {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD_EXP");
        order.setOrderStatus(3);
        order.setOrderAmount(10000L);
        order.setProductTitle("Export Product");
        order.setOrderCreateTime(LocalDateTime.now());

        Page<ColonelsettlementOrder> orderPage = new Page<>(1, 2000);
        orderPage.setRecords(List.of(order));
        orderPage.setTotal(1);
        orderPage.setPages(1);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(orderPage);
        OrderPerformanceBatchResponse emptyPerfResponse = new OrderPerformanceBatchResponse();
        emptyPerfResponse.setItems(List.of());
        when(orderPerformanceQueryFacade.batchGetOrderPerformance(any(), any())).thenReturn(emptyPerfResponse);
        org.mockito.Mockito.lenient().when(activityMapper.selectNamesByActivityIds(any())).thenReturn(List.of());

        MockHttpServletResponse response = new MockHttpServletResponse();
        dataController.exportOrderDetail(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, UUID.randomUUID(), null, DataScope.ALL, response);

        assertThat(response.getContentType()).contains("text/csv");
        String content = response.getContentAsString();
        assertThat(content).contains("订单ID,活动信息,商品信息,合作方信息,推广者,渠道,招商,订单状态,订单额,服务费收入,技术服务费,服务费支出,服务费收益,招商提成,渠道提成,毛利,订单时间");
        assertThat(content).contains("ORD_EXP");
        assertThat(content).contains("结算：-");
    }

    @Test
    void exportOrderDetail_shouldHaveCorrectRoleAnnotation() throws Exception {
        Method exportOrderDetail = DataController.class.getDeclaredMethod(
                "exportOrderDetail",
                String.class, String.class, UUID.class, String.class,
                String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class,
                LocalDate.class, LocalDate.class, String.class,
                String.class, String.class,
                UUID.class, UUID.class, DataScope.class,
                jakarta.servlet.http.HttpServletResponse.class
        );
        assertThat(exportOrderDetail.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(exportOrderDetail.getAnnotation(RequireRoles.class).value())
                .containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER);
    }
}
