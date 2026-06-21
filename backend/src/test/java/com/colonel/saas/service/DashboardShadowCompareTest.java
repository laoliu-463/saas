package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DDD-ANALYTICS-002: DashboardShadowCompareService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DashboardShadowCompareTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 5, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 5, 31, 23, 59);

    // ─── isEnabled ─────────────────────────────────────────────────

    @Test
    void isEnabled_returnsFalse_whenConfigFalse() {
        var svc = new DashboardShadowCompareService(mock(PerformanceMetricsQueryService.class), false);
        assertThat(svc.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_returnsTrue_whenConfigTrue() {
        var svc = new DashboardShadowCompareService(mock(PerformanceMetricsQueryService.class), true);
        assertThat(svc.isEnabled()).isTrue();
    }

    // ─── compare() disabled ────────────────────────────────────────

    @Test
    void compare_returnsNull_whenDisabled() {
        var perf = mock(PerformanceMetricsQueryService.class);
        var svc = new DashboardShadowCompareService(perf, false);

        DashboardService.Summary summary = new DashboardService.Summary();
        var result = svc.compare(summary, START, END, null, null, DataScope.ALL);

        assertThat(result).isNull();
        verifyNoInteractions(perf);
    }

    // ─── compare() enabled, all match ──────────────────────────────

    @Test
    void compare_allPass_whenSettleValuesMatch() {
        var perf = mock(PerformanceMetricsQueryService.class);
        var svc = new DashboardShadowCompareService(perf, true);

        // old summary: orderCount=10, orderAmount=50000 (cent), serviceFee=2300 (yuan → 230000 cent)
        DashboardService.Summary summary = new DashboardService.Summary();
        summary.setOrderCount(10L);
        summary.setOrderAmount(50000L);
        summary.setServiceFee(2300L);

        var settleAgg = new PerformanceMetricsQueryService.PerformanceAggregate(
                10L, 50000L, 230000L, 5000L, 10000L, 3000L, 8000L, 2000L, 1500L, 4500L);
        var estimateAgg = new PerformanceMetricsQueryService.PerformanceAggregate(
                12L, 60000L, 250000L, 6000L, 12000L, 3500L, 9000L, 2500L, 1800L, 5200L);

        when(perf.aggregateRange(eq(START), eq(END), eq("settle_time"), any(), any(), any()))
                .thenReturn(settleAgg);
        when(perf.aggregateRange(eq(START), eq(END), eq("create_time"), any(), any(), any()))
                .thenReturn(estimateAgg);

        var result = svc.compare(summary, START, END, null, null, DataScope.ALL);

        assertThat(result).isNotNull();
        assertThat(result.allMatch()).isTrue();

        // settle track: 3 overlapping metrics all match
        var settleDiffs = result.settleTrack().metricDiffs();
        assertThat(settleDiffs).hasSize(9);
        assertThat(settleDiffs.stream().filter(d -> d.oldValue() != null && d.diff() != 0)).isEmpty();

        // estimate track: all new-path-only (oldValue == null)
        var estimateDiffs = result.estimateTrack().metricDiffs();
        assertThat(estimateDiffs).hasSize(9);
        assertThat(estimateDiffs).allMatch(d -> d.oldValue() == null);
    }

    // ─── compare() enabled, diff exists ────────────────────────────

    @Test
    void compare_detectsDiff_whenSettleValuesDiffer() {
        var perf = mock(PerformanceMetricsQueryService.class);
        var svc = new DashboardShadowCompareService(perf, true);

        DashboardService.Summary summary = new DashboardService.Summary();
        summary.setOrderCount(10L);
        summary.setOrderAmount(50000L);
        summary.setServiceFee(2300L); // 2300 yuan = 230000 cent

        // settle: orderCount differs (10 vs 12)
        var settleAgg = new PerformanceMetricsQueryService.PerformanceAggregate(
                12L, 50000L, 230000L, 5000L, 10000L, 3000L, 8000L, 2000L, 1500L, 4500L);
        var estimateAgg = new PerformanceMetricsQueryService.PerformanceAggregate(
                12L, 60000L, 250000L, 6000L, 12000L, 3500L, 9000L, 2500L, 1800L, 5200L);

        when(perf.aggregateRange(eq(START), eq(END), eq("settle_time"), any(), any(), any()))
                .thenReturn(settleAgg);
        when(perf.aggregateRange(eq(START), eq(END), eq("create_time"), any(), any(), any()))
                .thenReturn(estimateAgg);

        var result = svc.compare(summary, START, END, null, null, DataScope.ALL);

        assertThat(result).isNotNull();
        assertThat(result.allMatch()).isFalse();

        var orderCountDiff = result.settleTrack().metricDiffs().stream()
                .filter(d -> "orderCount".equals(d.metric()))
                .findFirst().orElseThrow();
        assertThat(orderCountDiff.oldValue()).isEqualTo(10L);
        assertThat(orderCountDiff.newValue()).isEqualTo(12L);
        assertThat(orderCountDiff.diff()).isEqualTo(2L);
    }

    // ─── compare() with null old values ────────────────────────────

    @Test
    void compare_handlesNullSummaryFields() {
        var perf = mock(PerformanceMetricsQueryService.class);
        var svc = new DashboardShadowCompareService(perf, true);

        DashboardService.Summary summary = new DashboardService.Summary();
        // all fields null

        var settleAgg = new PerformanceMetricsQueryService.PerformanceAggregate(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        var estimateAgg = settleAgg;

        when(perf.aggregateRange(any(), any(), eq("settle_time"), any(), any(), any()))
                .thenReturn(settleAgg);
        when(perf.aggregateRange(any(), any(), eq("create_time"), any(), any(), any()))
                .thenReturn(estimateAgg);

        var result = svc.compare(summary, START, END, null, null, DataScope.ALL);

        assertThat(result).isNotNull();
        assertThat(result.allMatch()).isTrue();
    }

    // ─── compare() exception handling ──────────────────────────────

    @Test
    void compare_returnsNull_whenNewPathThrows() {
        var perf = mock(PerformanceMetricsQueryService.class);
        var svc = new DashboardShadowCompareService(perf, true);

        when(perf.aggregateRange(any(), any(), eq("settle_time"), any(), any(), any()))
                .thenThrow(new RuntimeException("DB connection failed"));

        DashboardService.Summary summary = new DashboardService.Summary();
        summary.setOrderCount(5L);

        var result = svc.compare(summary, START, END, null, null, DataScope.ALL);
        assertThat(result).isNull();
    }

    // ─── compare() passes correct scope ────────────────────────────

    @Test
    void compare_passesCorrectScopeParameters() {
        var perf = mock(PerformanceMetricsQueryService.class);
        var svc = new DashboardShadowCompareService(perf, true);

        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID deptId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        var agg = new PerformanceMetricsQueryService.PerformanceAggregate(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        when(perf.aggregateRange(any(), any(), any(), eq(userId), eq(deptId), eq(DataScope.PERSONAL)))
                .thenReturn(agg);

        DashboardService.Summary summary = new DashboardService.Summary();
        summary.setOrderCount(0L);
        summary.setOrderAmount(0L);
        summary.setServiceFee(0L);

        svc.compare(summary, START, END, userId, deptId, DataScope.PERSONAL);

        verify(perf, times(2)).aggregateRange(
                eq(START), eq(END), any(), eq(userId), eq(deptId), eq(DataScope.PERSONAL));
    }

    // ─── DashboardService integration (shadow not injected) ────────

    @Test
    void dashboardService_getSummary_works_whenShadowServiceIsNull() {
        // This tests that DashboardService works without shadow service injected
        // (the @Autowired(required=false) field remains null)
        var orderMapper = mock(com.colonel.saas.mapper.ColonelsettlementOrderMapper.class);
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var perf = mock(PerformanceMetricsQueryService.class);

        when(perf.hasPerformanceRecords()).thenReturn(false);
        when(orderMapper.selectMaps(any())).thenReturn(java.util.List.of(
                java.util.Map.of("ordercount", 5L, "orderamount", 10000L, "servicefee", 200L)));
        when(orderMapper.selectCount(any())).thenReturn(3L).thenReturn(2L);
        when(jdbc.queryForList(any(), any(Object[].class)))
                .thenReturn(java.util.List.of())
                .thenReturn(java.util.List.of(java.util.Map.of("total_count", 0L)))
                .thenReturn(java.util.List.of());

        var dashboardService = new DashboardService(
                orderMapper, jdbc, perf, new com.colonel.saas.domain.user.policy.DataScopePolicy());
        // shadowCompareService is null by default (field injection not done in unit test)

        DashboardService.Summary summary = dashboardService.getSummary(START, END, null, null, DataScope.ALL);

        assertThat(summary).isNotNull();
        assertThat(summary.getOrderCount()).isEqualTo(5L);
    }
}
