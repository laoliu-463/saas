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
import com.colonel.saas.service.ShortTtlCacheService;
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

    private DataController dataController;

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
                new ShortTtlCacheService()
        );
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
        CommissionService.CommissionSummary summary =
                new CommissionService.CommissionSummary(100000L, 20000L, 30000L,
                        50000L, 25000L, 12500L, 12500L,
                        java.math.BigDecimal.valueOf(0.5), java.math.BigDecimal.valueOf(0.25));
        when(commissionService.calculateByActivityBuckets(any())).thenReturn(summary);

        var response = dataController.getMetrics(null, userId, null, DataScope.ALL);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getTrend7d()).hasSize(7);
        assertThat(response.getData().getTodayGmv()).isNotNull();
        assertThat(response.getData().getServiceFee()).isNotNull();
        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = queryWrapperCaptor();
        verify(orderMapper, times(4)).selectMaps(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues().get(0).getSqlSegment()).contains("create_time");
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

        var response = dataController.getMetrics(null, userId, UUID.randomUUID(), DataScope.PERSONAL);

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

        var response = dataController.getMetrics(null, userId, deptId, DataScope.DEPT);

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

        var response = dataController.getMetrics("createTime", UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);

        assertThat(response.getCode()).isEqualTo(200);
        verify(orderMapper, never()).selectPage(any(Page.class), any());
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
    void getOrderPage_withStatusFilter_addsStatusCondition() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderPage(1, 10, null, "SHIPPED", null, null, null, null, null,
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

        dataController.getOrderPage(1, 10, "MOCK_GEN_ATTR", null, null, null, null, null, null,
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
    void sensitiveDataEndpoints_shouldRequireAdminOrLeaderRoles() throws Exception {
        Method exportOrders = DataController.class.getMethod(
                "exportOrders",
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
