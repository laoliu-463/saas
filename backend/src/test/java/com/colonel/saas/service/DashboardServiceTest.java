package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PerformanceMetricsQueryService performanceMetricsQueryService;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(orderMapper, jdbcTemplate, performanceMetricsQueryService, new DataScopePolicy());
        lenient().when(performanceMetricsQueryService.hasPerformanceRecords()).thenReturn(false);
    }

    @Test
    void getSummary_shouldKeepAttributedCountFromOrderFacts() {
        mockBaseOrderAggregates(5L, 2L);
        mockJdbcSequences(
                List.of(
                Map.of("category", DashboardService.DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE, "total_count", 2L),
                Map.of("category", DashboardService.DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION, "total_count", 1L)
                ),
                1L,
                List.of(productRow("3223881", "3814081914181124118", "命中商品"))
        );

        DashboardService.Summary summary = service.getSummary(LocalDateTime.now().minusDays(7), LocalDateTime.now(), null, null, DataScope.ALL);

        assertThat(summary.getAttributedOrderCount()).isEqualTo(5L);
        assertThat(summary.getUnattributedOrderCount()).isEqualTo(2L);
        assertThat(summary.getUnsafeBecauseCreatedAfterOrderCount()).isEqualTo(2L);
        assertThat(summary.getOrderCount()).isEqualTo(7L);
        assertThat(summary.getAttributionRate()).isEqualTo(5D / 7D);
    }

    @Test
    void getSummary_shouldExposeUnsafeNativeMatchAsDiagnosticInsteadOfAttributedPerformance() {
        mockBaseOrderAggregates(10L, 4L);
        mockJdbcSequences(
                List.of(Map.of("category", DashboardService.DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE, "total_count", 4L)),
                0L,
                List.of()
        );

        DashboardService.Summary summary = service.getSummary(null, null, null, null, DataScope.ALL);

        assertThat(summary.getUnsafeBecauseCreatedAfterOrderCount()).isEqualTo(4L);
        assertThat(summary.getAttributedOrderCount()).isEqualTo(10L);
        assertThat(summary.getChannelPerformance()).isEmpty();
        assertThat(summary.getColonelPerformance()).isEmpty();
    }

    @Test
    void getSummary_shouldExposeUpstreamMissingWhenProductFactsAreMissing() {
        mockBaseOrderAggregates(3L, 5L);
        mockJdbcSequences(
                List.of(Map.of("category", DashboardService.DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED, "total_count", 5L)),
                0L,
                List.of()
        );

        DashboardService.Summary summary = service.getSummary(null, null, null, null, DataScope.ALL);

        assertThat(summary.getUpstreamProductUncoveredCount()).isEqualTo(5L);
        assertThat(summary.getDiagnosticBreakdown())
                .extracting(DashboardService.DiagnosticItem::getCategory)
                .containsExactly(DashboardService.DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED);
        assertThat(summary.getDiagnosticBreakdown().get(0).getDrillDownQuery().attributionStatus()).isNull();
        verify(jdbcTemplate, times(3)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void getSummary_shouldUsePerformanceRecordsWhenAvailable() {
        when(performanceMetricsQueryService.hasPerformanceRecords()).thenReturn(true);
        when(performanceMetricsQueryService.aggregateDashboardSummary(any(), any(), any(), any(), any()))
                .thenReturn(new PerformanceMetricsQueryService.DashboardPerformanceSummary(
                        8L,
                        90000L,
                        1800L,
                        List.of(new PerformanceMetricsQueryService.PerformanceLeaderboardItem(
                                "channel-1", "渠道A", 3L, 30000L, 600L)),
                        List.of(new PerformanceMetricsQueryService.PerformanceLeaderboardItem(
                                "colonel-1", "招商A", 2L, 20000L, 400L))));
        when(orderMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(6L)
                .thenReturn(2L);
        mockJdbcSequences(List.of(), 0L, List.of());

        DashboardService.Summary summary = service.getSummary(null, null, null, null, DataScope.ALL);

        assertThat(summary.getOrderCount()).isEqualTo(8L);
        assertThat(summary.getOrderAmount()).isEqualTo(90000L);
        assertThat(summary.getServiceFee()).isEqualTo(1800L);
        assertThat(summary.getChannelPerformance()).hasSize(1);
        assertThat(summary.getChannelPerformance().get(0).getChannelUserName()).isEqualTo("渠道A");
        assertThat(summary.getColonelPerformance()).hasSize(1);
        assertThat(summary.getColonelPerformance().get(0).getColonelUserName()).isEqualTo("招商A");
    }

    @Test
    void getSummary_shouldUseOrderTableFactsAndReturnBreakdownDrilldown() {
        mockBaseOrderAggregates(8L, 2L);
        mockJdbcSequences(
                List.of(Map.of("category", DashboardService.DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION, "total_count", 2L)),
                1L,
                List.of(productRow("3559407", "3810204346914832817", "空样本商品"))
        );

        DashboardService.Summary summary = service.getSummary(null, null, null, null, DataScope.ALL);

        assertThat(summary.getOrderCount()).isEqualTo(10L);
        assertThat(summary.getOrderAmount()).isEqualTo(120000L);
        assertThat(summary.getServiceFee()).isEqualTo(2300L);
        assertThat(summary.getActivityProductBreakdown()).hasSize(1);
        DashboardService.ActivityProductItem item = summary.getActivityProductBreakdown().get(0);
        assertThat(item.getActivityId()).isEqualTo("3559407");
        assertThat(item.getProductId()).isEqualTo("3810204346914832817");
        assertThat(item.getDrillDownQuery().activityId()).isEqualTo("3559407");
        assertThat(item.getDrillDownQuery().productId()).isEqualTo("3810204346914832817");
        assertThat(item.getDrillDownQuery().timeField()).isEqualTo("settleTime");

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper, times(2)).selectCount(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues()).isNotEmpty();
    }

    @Test
    void getSummary_shouldReturnZeroAttributionRateWhenThereAreNoOrders() {
        mockBaseOrderAggregates(0L, 0L);
        mockJdbcSequences(List.of(), 0L, List.of());

        DashboardService.Summary summary = service.getSummary(null, null, null, null, DataScope.ALL);

        assertThat(summary.getOrderCount()).isZero();
        assertThat(summary.getAttributedOrderCount()).isZero();
        assertThat(summary.getUnattributedOrderCount()).isZero();
        assertThat(summary.getAttributionRate()).isZero();
    }

    @Test
    void getActivityProductBreakdown_shouldNormalizePagingAndApplyPersonalScope() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("total_count", 1L)))
                .thenReturn(List.of(productRow("activity-1", "product-1", "个人范围商品")));

        DashboardService.ActivityProductPage page =
                service.getActivityProductBreakdown(null, null, userId, null, DataScope.PERSONAL, 0, 0);

        assertThat(page.page()).isEqualTo(1L);
        assertThat(page.size()).isEqualTo(1L);
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).hasSize(1);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).queryForList(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getAllValues().get(0)).contains("co.user_id = ?");
        assertThat(argsCaptor.getAllValues().get(0)).containsExactly(userId);
        assertThat(argsCaptor.getAllValues().get(1)).containsExactly(userId, 1L, 0L);
    }

    @Test
    void getActivityProductBreakdown_shouldApplyRangeAndDeptScopeBeforePagingArgs() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 31, 23, 59);
        UUID deptId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("total_count", 0L)))
                .thenReturn(List.of());

        DashboardService.ActivityProductPage page =
                service.getActivityProductBreakdown(start, end, null, deptId, DataScope.DEPT, 2, 3);

        assertThat(page.page()).isEqualTo(2L);
        assertThat(page.size()).isEqualTo(3L);
        assertThat(page.records()).isEmpty();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).queryForList(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getAllValues().get(0))
                .contains("co.settle_time >= ?")
                .contains("co.settle_time <= ?")
                .contains("co.dept_id = ?");
        assertThat(argsCaptor.getAllValues().get(0)).containsExactly(start, end, deptId);
        assertThat(argsCaptor.getAllValues().get(1)).containsExactly(start, end, deptId, 3L, 3L);
    }

    @Test
    void applyScope_shouldKeepFailClosedBehaviorWhenRestrictedContextIsMissing() {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        invokeApplyScope(wrapper, null, UUID.randomUUID(), DataScope.PERSONAL);

        assertThat(wrapper.getSqlSegment()).contains("1 = 0");
    }

    @Test
    void normalizeDiagnosisCategory_shouldAcceptOnlyKnownCategoriesAndLegacyAlias() {
        assertThat(DashboardService.normalizeDiagnosisCategory(null)).isNull();
        assertThat(DashboardService.normalizeDiagnosisCategory("   ")).isNull();
        assertThat(DashboardService.normalizeDiagnosisCategory("UNSAFE_BECAUSE_CREATED_AFTER_ORDER"))
                .isEqualTo(DashboardService.DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE);
        assertThat(DashboardService.normalizeDiagnosisCategory(" " + DashboardService.DIAGNOSIS_NATIVE_KEY_MISMATCH + " "))
                .isEqualTo(DashboardService.DIAGNOSIS_NATIVE_KEY_MISMATCH);
        assertThat(DashboardService.normalizeDiagnosisCategory("UPSTREAM_PRODUCT_UNCOVERED' OR '1'='1")).isNull();
    }

    @Test
    void valueObjects_shouldExposeAssignedValues() {
        DashboardService.PerformanceItem performanceItem = new DashboardService.PerformanceItem();
        performanceItem.setChannelUserId("channel-1");
        performanceItem.setChannelUserName("渠道A");
        performanceItem.setColonelUserId("colonel-1");
        performanceItem.setColonelUserName("招商A");
        performanceItem.setOrderCount(1L);
        performanceItem.setOrderAmount(2L);
        performanceItem.setServiceFee(3L);

        DashboardService.ActivityProductItem activityProductItem = new DashboardService.ActivityProductItem();
        DashboardService.DrillDownQuery drillDownQuery =
                new DashboardService.DrillDownQuery("activity-1", "product-1", null, null, null, "settleTime");
        activityProductItem.setActivityId("activity-1");
        activityProductItem.setProductId("product-1");
        activityProductItem.setProductName("商品A");
        activityProductItem.setProductCover("https://img.example/a.png");
        activityProductItem.setBizStatus("LINKED");
        activityProductItem.setAssigneeName("招商B");
        activityProductItem.setOrderCount(4L);
        activityProductItem.setOrderAmount(5L);
        activityProductItem.setUnattributedOrderCount(6L);
        activityProductItem.setMappingCount(7L);
        activityProductItem.setPromotionLinkCount(8L);
        activityProductItem.setDrillDownQuery(drillDownQuery);

        DashboardService.ActivityProductPage page =
                new DashboardService.ActivityProductPage(1L, 2L, 3L, List.of(activityProductItem));

        assertThat(performanceItem.getChannelUserId()).isEqualTo("channel-1");
        assertThat(performanceItem.getChannelUserName()).isEqualTo("渠道A");
        assertThat(performanceItem.getColonelUserId()).isEqualTo("colonel-1");
        assertThat(performanceItem.getColonelUserName()).isEqualTo("招商A");
        assertThat(performanceItem.getOrderCount()).isEqualTo(1L);
        assertThat(performanceItem.getOrderAmount()).isEqualTo(2L);
        assertThat(performanceItem.getServiceFee()).isEqualTo(3L);
        assertThat(activityProductItem.getActivityId()).isEqualTo("activity-1");
        assertThat(activityProductItem.getProductId()).isEqualTo("product-1");
        assertThat(activityProductItem.getProductName()).isEqualTo("商品A");
        assertThat(activityProductItem.getProductCover()).isEqualTo("https://img.example/a.png");
        assertThat(activityProductItem.getBizStatus()).isEqualTo("LINKED");
        assertThat(activityProductItem.getAssigneeName()).isEqualTo("招商B");
        assertThat(activityProductItem.getOrderCount()).isEqualTo(4L);
        assertThat(activityProductItem.getOrderAmount()).isEqualTo(5L);
        assertThat(activityProductItem.getUnattributedOrderCount()).isEqualTo(6L);
        assertThat(activityProductItem.getMappingCount()).isEqualTo(7L);
        assertThat(activityProductItem.getPromotionLinkCount()).isEqualTo(8L);
        assertThat(activityProductItem.getDrillDownQuery()).isEqualTo(drillDownQuery);
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.page()).isEqualTo(2L);
        assertThat(page.size()).isEqualTo(3L);
        assertThat(page.records()).containsExactly(activityProductItem);
    }

    @Test
    void deriveSettlementReason_zeroTotal_returnsEmptyMessage() {
        assertThat(invokeReason(0L, 0L)).isEqualTo("暂无订单数据");
    }

    @Test
    void deriveSettlementReason_zeroSettledNonZeroTotal_returnsUpstreamPendingMessage() {
        assertThat(invokeReason(0L, 100L))
                .isEqualTo("上游尚未回传结算样本，当前仅展示下单/同步侧指标");
    }

    @Test
    void deriveSettlementReason_partialSettled_returnsRatioMessage() {
        assertThat(invokeReason(250L, 1000L)).isEqualTo("已结算订单 250 / 1000（25%）");
    }

    @Test
    void deriveSettlementReason_fullySettled_returnsFullRatio() {
        assertThat(invokeReason(5591L, 5591L)).isEqualTo("已结算订单 5591 / 5591（100%）");
    }

    @Test
    void formatRange_bothNull_returnsUnboundedMessage() {
        assertThat(invokeFormat(null, null)).isEqualTo("未指定时间范围");
    }

    @Test
    void formatRange_onlyStart_returnsStartLabel() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0, 0);
        assertThat(invokeFormat(start, null)).isEqualTo("开始于 2026-06-01 00:00:00");
    }

    @Test
    void formatRange_onlyEnd_returnsEndLabel() {
        LocalDateTime end = LocalDateTime.of(2026, 6, 6, 23, 59, 59);
        assertThat(invokeFormat(null, end)).isEqualTo("截至 2026-06-06 23:59:59");
    }

    @Test
    void formatRange_bothPresent_returnsRange() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 6, 23, 59, 59);
        assertThat(invokeFormat(start, end))
                .isEqualTo("2026-06-01 00:00:00 ~ 2026-06-06 23:59:59");
    }

    @Test
    void summary_settlementFieldsRoundTrip() {
        DashboardService.Summary summary = new DashboardService.Summary();
        LocalDateTime now = LocalDateTime.of(2026, 6, 6, 12, 0, 0);

        summary.setSettledOrderCount(250L);
        summary.setSnapshotAt(now);
        summary.setSettlementReason("已结算订单 250 / 1000（25%）");
        summary.setSettleTimeRange("2026-06-01 00:00:00 ~ 2026-06-06 23:59:59");

        assertThat(summary.getSettledOrderCount()).isEqualTo(250L);
        assertThat(summary.getSnapshotAt()).isEqualTo(now);
        assertThat(summary.getSettlementReason()).isEqualTo("已结算订单 250 / 1000（25%）");
        assertThat(summary.getSettleTimeRange())
                .isEqualTo("2026-06-01 00:00:00 ~ 2026-06-06 23:59:59");
    }

    @Test
    void summary_settlementFieldsDefaultToNull() {
        DashboardService.Summary summary = new DashboardService.Summary();
        assertThat(summary.getSettledOrderCount()).isNull();
        assertThat(summary.getSnapshotAt()).isNull();
        assertThat(summary.getSettlementReason()).isNull();
        assertThat(summary.getSettleTimeRange()).isNull();
    }

    private String invokeReason(long settled, long total) {
        try {
            Method m = DashboardService.class.getDeclaredMethod(
                    "deriveSettlementReason", long.class, long.class);
            m.setAccessible(true);
            return (String) m.invoke(service, settled, total);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to invoke deriveSettlementReason", ex);
        }
    }

    private String invokeFormat(LocalDateTime start, LocalDateTime end) {
        try {
            Method m = DashboardService.class.getDeclaredMethod(
                    "formatRange", LocalDateTime.class, LocalDateTime.class);
            m.setAccessible(true);
            return (String) m.invoke(service, start, end);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to invoke formatRange", ex);
        }
    }

    private void invokeApplyScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        try {
            Method m = DashboardService.class.getDeclaredMethod(
                    "applyScope", QueryWrapper.class, UUID.class, UUID.class, DataScope.class);
            m.setAccessible(true);
            m.invoke(service, wrapper, userId, deptId, dataScope);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to invoke applyScope", ex);
        }
    }

    private void mockBaseOrderAggregates(long attributedCount, long unattributedCount) {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("ordercount", attributedCount + unattributedCount, "orderamount", 120000L, "servicefee", 2300L)))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(orderMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(attributedCount)
                .thenReturn(unattributedCount);
    }

    private void mockJdbcSequences(List<Map<String, Object>> diagnostics, long total, List<Map<String, Object>> rows) {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(diagnostics)
                .thenReturn(List.of(Map.of("total_count", total)))
                .thenReturn(rows);
    }

    private Map<String, Object> productRow(String activityId, String productId, String productName) {
        return Map.ofEntries(
                Map.entry("activity_id", activityId),
                Map.entry("product_id", productId),
                Map.entry("product_name", productName),
                Map.entry("product_cover", "https://img.example/test.png"),
                Map.entry("biz_status", "LINKED"),
                Map.entry("assignee_name", "招商A"),
                Map.entry("order_count", 3L),
                Map.entry("order_amount", 4500L),
                Map.entry("unattributed_order_count", 1L),
                Map.entry("mapping_count", 1L),
                Map.entry("promotion_link_count", 1L)
        );
    }
}
