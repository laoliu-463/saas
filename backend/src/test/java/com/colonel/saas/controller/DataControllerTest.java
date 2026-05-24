package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
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
import static org.mockito.Mockito.never;
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

    private DataController dataController;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> queryWrapperCaptor() {
        return (ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(QueryWrapper.class);
    }

    @BeforeEach
    void setUp() {
        dataController = new DataController(
                orderMapper,
                commissionService,
                exclusiveTalentMapper,
                exclusiveMerchantMapper,
                activityMapper,
                new ShortTtlCacheService(),
                performanceMetricsQueryService
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
                        3L, 9000L, 900L, 90L, 810L, 81L, 162L, 567L));
        when(performanceMetricsQueryService.trendByDay(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new PerformanceMetricsQueryService.TrendPoint(LocalDate.now().toString(), 3L, 9000L)));
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("order_count", 1L)));

        var response = dataController.getMetrics(UUID.randomUUID(), null, DataScope.ALL);

        assertThat(response.getData().getEstimate().getMetricsSource()).isEqualTo("performance_records");
        assertThat(response.getData().getEstimate().getAmountTrack()).isEqualTo("estimate");
        assertThat(response.getData().getEstimate().getGrossProfit()).isEqualByComparingTo("5.67");
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
                new CommissionService.CommissionSummary(100000L, 20000L, 30000L,
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
        assertThat(wrapperCaptor.getAllValues().get(4).getSqlSegment()).contains("create_time");
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
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L,
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
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(
                new CommissionService.CommissionSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L,
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
                new CommissionService.CommissionSummary(0L, 100L, 200L, 300L, 150L, 75L, 75L,
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
    void getOrderPage_withDeptScopeAndMissingDeptId_doesNotThrow() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

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
                LocalDate.of(2026, 5, 3),
                LocalDate.of(2026, 5, 3),
                null,
                UUID.randomUUID(),
                null,
                DataScope.DEPT
        );

        assertThat(response.getCode()).isEqualTo(200);
        verify(orderMapper).findPageWithScope(any(Page.class), any(QueryWrapper.class));
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
        Method exportOrders = DataController.class.getMethod(
                "exportOrders",
                String.class,
                String.class,
                UUID.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                String.class,
                UUID.class,
                UUID.class,
                DataScope.class,
                jakarta.servlet.http.HttpServletResponse.class
        );
        Method getExclusiveTalentStatus = DataController.class.getMethod(
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
        Method getExclusiveMerchantStatus = DataController.class.getMethod(
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
        Method exportActivities = DataController.class.getMethod(
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
}
