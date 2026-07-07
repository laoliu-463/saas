package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderAttributionService 单测。
 *
 * <p>t2-orders 抽 service:验证未归因订单 wrapper 拼装 + 30 天摘要聚合 + 数据范围
 * 应用。不依赖 MyBatis-Plus lambda cache。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderAttributionServiceTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;

    private OrderAttributionService service;
    private DataScopeResolver dataScopeResolver;
    private DddRefactorProperties dddRefactorProperties;

    @BeforeEach
    void setUp() {
        dataScopeResolver = spy(new DataScopeResolver(new DataScopePolicy()));
        dddRefactorProperties = new DddRefactorProperties();
        service = new OrderAttributionService(orderMapper, dataScopeResolver, dddRefactorProperties);
    }

    // ============================================================
    // findUnattributedPage — wrapper 拼装 + 时间窗口
    // ============================================================

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findUnattributedPage_shouldInjectCoAliasedUnattributedFilterAndTimeWindow() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 28);
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        IPage<ColonelsettlementOrder> mockPage = new Page<>(1, 10, 0);
        when(orderMapper.findPageWithScope(any(Page.class), any())).thenReturn(mockPage);

        IPage<ColonelsettlementOrder> result = service.findUnattributedPage(
                1L, 10L, start, end, userId, deptId, DataScope.DEPT);

        assertThat(result).isSameAs(mockPage);

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).findPageWithScope(any(Page.class), captor.capture());
        QueryWrapper<ColonelsettlementOrder> wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        // 表别名 co. + 状态过滤 + 时间半开区间
        assertThat(sql).contains("co.settle_time");
        assertThat(sql).contains("co.attribution_status");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("UNATTRIBUTED");
        // DEPT 范围:dept_id 过滤
        assertThat(sql).contains("co.dept_id");
        assertThat(sql).doesNotContain("co.user_id");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findUnattributedPage_personalScopeShouldFilterByUserId() {
        UUID userId = UUID.randomUUID();
        when(orderMapper.findPageWithScope(any(Page.class), any())).thenReturn(new Page<>(1, 10, 0));
        service.findUnattributedPage(1L, 10L, null, null, userId, null, DataScope.PERSONAL);
        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).findPageWithScope(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("co.user_id");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findUnattributedPage_shouldKeepLegacyDataScopeWhenPolicyDisabled() {
        UUID userId = UUID.randomUUID();
        when(orderMapper.findPageWithScope(any(Page.class), any())).thenReturn(new Page<>(1, 10, 0));

        service.findUnattributedPage(1L, 10L, null, null, userId, null, DataScope.PERSONAL);

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).findPageWithScope(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("co.user_id");
        verify(dataScopeResolver, never()).applyTo(
                any(QueryWrapper.class),
                any(),
                any(),
                any(),
                any(String.class),
                any(String.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findUnattributedPage_dataScopePolicyEnabledPathShouldDelegateToUserPolicy() {
        UUID userId = UUID.randomUUID();
        dddRefactorProperties.getDataScopePolicy().setEnabled(true);
        when(orderMapper.findPageWithScope(any(Page.class), any())).thenReturn(new Page<>(1, 10, 0));

        service.findUnattributedPage(1L, 10L, null, null, userId, null, DataScope.PERSONAL);

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).findPageWithScope(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("co.user_id");
        verify(dataScopeResolver).applyTo(
                any(QueryWrapper.class),
                eq(userId),
                eq(null),
                eq(DataScope.PERSONAL),
                eq("co.user_id"),
                eq("co.dept_id"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void buildScopedQuery_shouldKeepLegacyDataScopeWhenPolicyDisabled() {
        UUID deptId = UUID.randomUUID();

        QueryWrapper<ColonelsettlementOrder> wrapper = service.buildScopedQuery(
                null,
                deptId,
                DataScope.DEPT);

        assertThat(wrapper.getSqlSegment()).contains("dept_id");
        verify(dataScopeResolver, never()).applyTo(
                any(QueryWrapper.class),
                any(),
                any(),
                any(),
                any(String.class),
                any(String.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void buildScopedQuery_dataScopePolicyEnabledPathShouldDelegateToUserPolicy() {
        UUID deptId = UUID.randomUUID();
        dddRefactorProperties.getDataScopePolicy().setEnabled(true);

        QueryWrapper<ColonelsettlementOrder> wrapper = service.buildScopedQuery(
                null,
                deptId,
                DataScope.DEPT);

        assertThat(wrapper.getSqlSegment()).contains("dept_id");
        verify(dataScopeResolver).applyTo(
                any(QueryWrapper.class),
                eq(null),
                eq(deptId),
                eq(DataScope.DEPT),
                eq("user_id"),
                eq("dept_id"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findUnattributedPage_nullDatesShouldDefaultTo30DayLookback() {
        when(orderMapper.findPageWithScope(any(Page.class), any())).thenReturn(new Page<>(1, 10, 0));
        service.findUnattributedPage(1L, 10L, null, null, UUID.randomUUID(), null, DataScope.ALL);
        // 半开区间 — lt 边界,没有传 endTime 也能跑通
        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).findPageWithScope(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("co.settle_time");
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains("UNATTRIBUTED");
    }

    // ============================================================
    // summarize — 30 天摘要聚合
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void summarize_shouldAggregateTotalAndPerformance() {
        // 模拟三段 selectMaps 调用:总计 + 渠道 + 团长
        UUID channelUserId = UUID.randomUUID();
        UUID colonelUserId = UUID.randomUUID();
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of(
                        "order_count", 10L,
                        "order_amount_cent", 300000L,
                        "service_fee_cent", 3000L,
                        "attributed_order_count", 6L,
                        "unattributed_order_count", 4L
                )))
                .thenReturn(List.of(Map.of(
                        "owner_id", channelUserId.toString(),
                        "order_count", 3L,
                        "order_amount_cent", 100000L,
                        "service_fee_cent", 1000L
                )))
                .thenReturn(List.of(Map.of(
                        "owner_id", colonelUserId.toString(),
                        "order_count", 2L,
                        "order_amount_cent", 50000L,
                        "service_fee_cent", 500L
                )));

        OrderAttributionService.SummaryResult result = service.summarize(
                UUID.randomUUID(), null, DataScope.ALL);

        assertThat(result.orderCount).isEqualTo(10L);
        assertThat(result.orderAmount).isEqualByComparingTo(BigDecimal.valueOf(3000.00));
        assertThat(result.serviceFee).isEqualByComparingTo(BigDecimal.valueOf(30.00));
        assertThat(result.attributedOrderCount).isEqualTo(6L);
        assertThat(result.unattributedOrderCount).isEqualTo(4L);
        assertThat(result.channelPerformance).hasSize(1);
        assertThat(result.channelPerformance.get(0).ownerId()).isEqualTo(channelUserId.toString());
        assertThat(result.colonelPerformance).hasSize(1);
        assertThat(result.colonelPerformance.get(0).ownerId()).isEqualTo(colonelUserId.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void summarize_emptyMapperResultShouldDefaultToZeroValues() {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of());

        OrderAttributionService.SummaryResult result = service.summarize(
                UUID.randomUUID(), null, DataScope.PERSONAL);

        assertThat(result.orderCount).isZero();
        assertThat(result.orderAmount).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.serviceFee).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.attributedOrderCount).isZero();
        assertThat(result.unattributedOrderCount).isZero();
        assertThat(result.channelPerformance).isEmpty();
        assertThat(result.colonelPerformance).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void summarize_caseInsensitiveColumnReadsShouldStillBind() {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of(
                        "ORDER_COUNT", 5L,
                        "ORDER_AMOUNT_CENT", 5000L,
                        "SERVICE_FEE_CENT", 50L,
                        "ATTRIBUTED_ORDER_COUNT", 3L,
                        "UNATTRIBUTED_ORDER_COUNT", 2L
                )))
                .thenReturn(List.of())
                .thenReturn(List.of());

        OrderAttributionService.SummaryResult result = service.summarize(
                UUID.randomUUID(), null, DataScope.ALL);

        assertThat(result.orderCount).isEqualTo(5L);
        assertThat(result.orderAmount).isEqualByComparingTo(BigDecimal.valueOf(50.00));
    }

    // ============================================================
    // toRow — 实体 → 视图对象
    // ============================================================

    @Test
    void toRow_shouldConvertCentToYuanAndMapAllFields() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-1");
        order.setProductId("P-1");
        order.setProductName("测试商品");
        order.setActivityId("ACT-1");
        order.setPickSource("MOCKPS01");
        order.setOrderAmount(12345L);
        order.setAttributionStatus("UNATTRIBUTED");
        order.setAttributionRemark("MAPPING_NOT_FOUND");
        order.setCreateTime(LocalDateTime.of(2026, 4, 25, 10, 0));

        OrderAttributionService.UnattributedOrderRow row = service.toRow(order);

        assertThat(row.orderId).isEqualTo("ORDER-1");
        assertThat(row.productId).isEqualTo("P-1");
        assertThat(row.productName).isEqualTo("测试商品");
        assertThat(row.activityId).isEqualTo("ACT-1");
        assertThat(row.pickSource).isEqualTo("MOCKPS01");
        assertThat(row.orderAmount).isEqualByComparingTo(BigDecimal.valueOf(123.45));
        assertThat(row.attributionStatus).isEqualTo("UNATTRIBUTED");
        assertThat(row.attributionRemark).isEqualTo("MAPPING_NOT_FOUND");
        assertThat(row.createTime).isEqualTo(LocalDateTime.of(2026, 4, 25, 10, 0));
    }

    // ============================================================
    // 数据范围 / 聚合辅助方法
    // ============================================================

    @Test
    void centToYuan_shouldHandleNullAndZero() {
        assertThat(service.centToYuan(null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(service.centToYuan(0L)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(service.centToYuan(100L)).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(service.centToYuan(12345L)).isEqualByComparingTo(BigDecimal.valueOf(123.45));
    }

    @Test
    void asLong_shouldHandleNullAndMalformedStrings() {
        assertThat(service.asLong(null, "x")).isZero();
        assertThat(service.asLong(Map.of("k", "bad"), "k")).isZero();
        assertThat(service.asLong(Map.of("k", 5L), "k")).isEqualTo(5L);
        assertThat(service.asLong(Map.of("K", 7L), "k")).isEqualTo(7L);
    }

    @Test
    void asString_shouldReturnEmptyStringForNull() {
        assertThat(service.asString(null, "x")).isEmpty();
        assertThat(service.asString(Map.of("K", "val"), "k")).isEqualTo("val");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildScopedQuery_shouldExcludeSoftDeleted() {
        QueryWrapper<ColonelsettlementOrder> wrapper = service.buildScopedQuery(
                UUID.randomUUID(), null, DataScope.ALL);
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("deleted");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSingleAggregate_shouldReturnEmptyMapForNullOrEmpty() {
        when(orderMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of());
        assertThat(service.getSingleAggregate(new QueryWrapper<>())).isEmpty();
    }
}
