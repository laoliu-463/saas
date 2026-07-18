package com.colonel.saas.domain.performance.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.service.PerformanceMetricsQueryService;
import com.colonel.saas.constant.RoleCodes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerformanceAggregateApplicationService 直接行为验证（DDD-PERFORMANCE Slice 2）。
 *
 * <p>核心目标：验证 Application 层独立持有完整的 aggregateRange SQL 装配逻辑，
 * 不依赖 Service 中转。Service 层委托是 thin shell，
 * 真实业务逻辑必须由本测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class PerformanceAggregateApplicationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PerformanceAggregateApplicationService applicationService;
    private DataScopePolicy dataScopePolicy;
    private DddRefactorProperties dddRefactorProperties;

    @BeforeEach
    void setUp() {
        dataScopePolicy = spy(new DataScopePolicy());
        dddRefactorProperties = new DddRefactorProperties();
        applicationService = new PerformanceAggregateApplicationService(
                jdbcTemplate,
                new DataScopeResolver(dataScopePolicy),
                dddRefactorProperties,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()));
    }

    @Test
    void aggregateRange_shouldUseEstimateColumnsForCreateTrack() {
        when(jdbcTemplate.queryForMap(contains("pr.estimate_service_fee"), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 3L,
                        "order_amount_cent", 7500L,
                        "service_fee_income_cent", 900L,
                        "tech_service_fee_cent", 90L,
                        "talent_commission_cent", 100L,
                        "service_profit_cent", 810L,
                        "recruiter_commission_cent", 81L,
                        "channel_commission_cent", 162L,
                        "gross_profit_cent", 567L));

        LocalDate today = LocalDate.now();
        PerformanceMetricsQueryService.PerformanceAggregate aggregate = applicationService.aggregateRange(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "createTime",
                UUID.randomUUID(),
                null,
                DataScope.ALL);

        assertThat(aggregate.orderCount()).isEqualTo(3L);
        assertThat(aggregate.grossProfitCent()).isEqualTo(567L);
        assertThat(aggregate.talentCommissionCent()).isEqualTo(100L);
    }

    @Test
    void aggregateRange_shouldUseEffectiveColumnsForSettleTrack() {
        when(jdbcTemplate.queryForMap(contains("pr.settle_amount"), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 2L,
                        "order_amount_cent", 6000L,
                        "service_fee_income_cent", 720L,
                        "tech_service_fee_cent", 72L,
                        "talent_commission_cent", 50L,
                        "service_profit_cent", 648L,
                        "recruiter_commission_cent", 64L,
                        "channel_commission_cent", 129L,
                        "gross_profit_cent", 455L));

        LocalDate today = LocalDate.now();
        PerformanceMetricsQueryService.PerformanceAggregate aggregate = applicationService.aggregateRange(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "settleTime",
                UUID.randomUUID(),
                null,
                DataScope.ALL);

        assertThat(aggregate.orderCount()).isEqualTo(2L);
        assertThat(aggregate.grossProfitCent()).isEqualTo(455L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("pr.settle_amount")
                .contains("pr.effective_service_fee")
                .contains("pr.effective_gross_profit")
                .contains("settle_time IS NOT NULL");
    }

    @Test
    void aggregateRange_shouldKeepLegacyPersonalScopeWhenPolicyDisabled() {
        when(jdbcTemplate.queryForMap(any(String.class), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 1L,
                        "order_amount_cent", 1000L,
                        "service_fee_income_cent", 100L,
                        "tech_service_fee_cent", 10L,
                        "talent_commission_cent", 0L,
                        "service_profit_cent", 90L,
                        "recruiter_commission_cent", 9L,
                        "channel_commission_cent", 18L,
                        "gross_profit_cent", 63L));

        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        applicationService.aggregateRange(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "createTime",
                userId,
                deptId,
                DataScope.PERSONAL);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), argsCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("pr.final_channel_user_id = ?")
                .doesNotContain("co.dept_id = ?");
        assertThat(argsCaptor.getValue()[0]).isEqualTo(userId);
        verify(dataScopePolicy, never()).decide(any(), any(), any());
    }

    @Test
    void aggregateRange_dataScopePolicyEnabledPathShouldDelegatePersonalScopeToUserPolicy() {
        dddRefactorProperties.getDataScopePolicy().setEnabled(true);
        when(jdbcTemplate.queryForMap(any(String.class), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 1L,
                        "order_amount_cent", 1000L,
                        "service_fee_income_cent", 100L,
                        "tech_service_fee_cent", 10L,
                        "talent_commission_cent", 0L,
                        "service_profit_cent", 90L,
                        "recruiter_commission_cent", 9L,
                        "channel_commission_cent", 18L,
                        "gross_profit_cent", 63L));

        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        applicationService.aggregateRange(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "createTime",
                userId,
                deptId,
                DataScope.PERSONAL);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), argsCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("pr.final_channel_user_id = ?")
                .doesNotContain("co.dept_id = ?");
        assertThat(argsCaptor.getValue()[0]).isEqualTo(userId);
        verify(dataScopePolicy).decide(userId, deptId, DataScope.PERSONAL);
    }

    @Test
    void aggregateRange_shouldScopeChannelStaffByFinalChannelUser() {
        when(jdbcTemplate.queryForMap(any(String.class), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 1L,
                        "order_amount_cent", 1000L,
                        "service_fee_income_cent", 100L,
                        "tech_service_fee_cent", 10L,
                        "service_fee_expense_cent", 0L,
                        "talent_commission_cent", 0L,
                        "service_profit_cent", 90L,
                        "recruiter_commission_cent", 9L,
                        "channel_commission_cent", 18L,
                        "gross_profit_cent", 63L));

        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        applicationService.aggregateRange(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "settleTime",
                userId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("pr.final_channel_user_id = ?")
                .doesNotContain("co.user_id = ?");
    }

    @Test
    void aggregateDashboardSummary_shouldScopeChannelStaffByFinalChannelUser() {
        when(jdbcTemplate.queryForMap(any(String.class), any(Object[].class)))
                .thenReturn(Map.of());
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenReturn(List.of());

        UUID userId = UUID.randomUUID();
        applicationService.aggregateDashboardSummary(
                LocalDate.now().minusDays(1).atStartOfDay(),
                LocalDate.now().atTime(23, 59, 59),
                userId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF));

        ArgumentCaptor<String> totalsSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(totalsSqlCaptor.capture(), any(Object[].class));
        assertThat(totalsSqlCaptor.getValue())
                .contains("pr.final_channel_user_id = ?")
                .doesNotContain("co.user_id = ?");

        ArgumentCaptor<String> leaderboardSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForList(leaderboardSqlCaptor.capture(), any(Object[].class));
        assertThat(leaderboardSqlCaptor.getAllValues())
                .allSatisfy(sql -> assertThat(sql).contains("pr.final_channel_user_id = ?"));
    }

    @Test
    void aggregateRange_shouldReadTalentCommissionFromOrderSettlementField() {
        when(jdbcTemplate.queryForMap(any(String.class), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 1L,
                        "order_amount_cent", 10000L,
                        "service_fee_income_cent", 1000L,
                        "tech_service_fee_cent", 100L,
                        "talent_commission_cent", 250L,
                        "service_profit_cent", 900L,
                        "recruiter_commission_cent", 90L,
                        "channel_commission_cent", 180L,
                        "gross_profit_cent", 630L));

        LocalDate today = LocalDate.now();
        applicationService.aggregateRange(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "settleTime",
                UUID.randomUUID(),
                null,
                DataScope.ALL);

        verify(jdbcTemplate).queryForMap(
                contains("pr.talent_commission"),
                any(Object[].class));
    }

    @Test
    void aggregateRange_businessLineChannelShouldAppendChannelFilter() {
        when(jdbcTemplate.queryForMap(any(String.class), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 0L,
                        "order_amount_cent", 0L,
                        "service_fee_income_cent", 0L,
                        "tech_service_fee_cent", 0L,
                        "talent_commission_cent", 0L,
                        "service_profit_cent", 0L,
                        "recruiter_commission_cent", 0L,
                        "channel_commission_cent", 0L,
                        "gross_profit_cent", 0L));

        LocalDate today = LocalDate.now();
        applicationService.aggregateRange(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "createTime",
                "CHANNEL",
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                DataScope.ALL);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("pr.final_channel_user_id IS NOT NULL")
                .contains("pr.final_channel_user_id = ?");
    }

    @Test
    void trendByDay_shouldUseTrackColumnsAndFillMissingDays() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "stat_date", start.plusDays(1).toString(),
                        "order_count", 4L,
                        "order_amount_cent", 12000L)));

        List<PerformanceMetricsQueryService.TrendPoint> trend = applicationService.trendByDay(
                start.atStartOfDay(),
                start.plusDays(3).atStartOfDay(),
                "createTime",
                UUID.randomUUID(),
                null,
                DataScope.ALL);

        assertThat(trend).containsExactly(
                new PerformanceMetricsQueryService.TrendPoint("2026-06-01", 0L, 0L),
                new PerformanceMetricsQueryService.TrendPoint("2026-06-02", 4L, 12000L),
                new PerformanceMetricsQueryService.TrendPoint("2026-06-03", 0L, 0L));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("DATE(pr.order_create_time) AS stat_date")
                .contains("COALESCE(SUM(pr.pay_amount + COALESCE(adj.delta_pay_amount, 0)), 0) AS order_amount_cent")
                .contains("GROUP BY DATE(pr.order_create_time)");
    }

    @Test
    void aggregateDashboardSummary_shouldBuildChannelAndRecruiterLeaderboards() {
        when(jdbcTemplate.queryForMap(any(String.class), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 7L,
                        "order_amount_cent", 42000L,
                        "service_fee_cent", 2100L));
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "user_id", "channel-1",
                        "user_name", "渠道A",
                        "order_count", 3L,
                        "order_amount_cent", 18000L,
                        "service_fee_cent", 900L)))
                .thenReturn(List.of(Map.of(
                        "user_id", "recruiter-1",
                        "user_name", "招商A",
                        "order_count", 2L,
                        "order_amount_cent", 12000L,
                        "service_fee_cent", 600L)));

        PerformanceMetricsQueryService.DashboardPerformanceSummary summary =
                applicationService.aggregateDashboardSummary(
                        LocalDateTime.of(2026, 6, 1, 0, 0),
                        LocalDateTime.of(2026, 6, 30, 23, 59, 59),
                        UUID.randomUUID(),
                        null,
                        DataScope.ALL);

        assertThat(summary.orderCount()).isEqualTo(7L);
        assertThat(summary.channelPerformance()).containsExactly(
                new PerformanceMetricsQueryService.PerformanceLeaderboardItem(
                        "channel-1", "渠道A", 3L, 18000L, 900L));
        assertThat(summary.colonelPerformance()).containsExactly(
                new PerformanceMetricsQueryService.PerformanceLeaderboardItem(
                        "recruiter-1", "招商A", 2L, 12000L, 600L));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForList(sqlCaptor.capture(), any(Object[].class));
        List<String> leaderboardSql = sqlCaptor.getAllValues();
        assertThat(leaderboardSql.get(0))
                .contains("pr.final_channel_user_id::text AS user_id")
                .contains("pr.final_channel_user_id IS NOT NULL")
                .contains("GROUP BY pr.final_channel_user_id")
                .contains("ORDER BY order_count DESC, order_amount_cent DESC")
                .contains("LIMIT 10");
        assertThat(leaderboardSql.get(1))
                .contains("pr.final_recruiter_user_id::text AS user_id")
                .contains("pr.final_recruiter_user_id IS NOT NULL")
                .contains("GROUP BY pr.final_recruiter_user_id")
                .contains("ORDER BY order_count DESC, order_amount_cent DESC")
                .contains("LIMIT 10");
    }

}
