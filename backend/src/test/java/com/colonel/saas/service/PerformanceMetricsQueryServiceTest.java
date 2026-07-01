package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.performance.application.PerformanceAggregateApplicationService;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceMetricsQueryServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PerformanceMetricsQueryService service;
    private PerformanceAggregateApplicationService aggregateApplicationService;
    private DataScopePolicy dataScopePolicy;
    private DddRefactorProperties dddRefactorProperties;

    @BeforeEach
    void setUp() {
        dataScopePolicy = spy(new DataScopePolicy());
        dddRefactorProperties = new DddRefactorProperties();
        // DDD-PERFORMANCE Slice 3: aggregateRange + trendByDay + aggregateDashboardSummary
        // 全部下沉至 application 层；测试使用同一份 mock jdbcTemplate/dataScopePolicy，
        // 保证 service → application 委派调用链上 SQL 装配 / DataScopePolicy 行为可被验证。
        aggregateApplicationService = new PerformanceAggregateApplicationService(
                jdbcTemplate, dataScopePolicy, dddRefactorProperties);
        service = new PerformanceMetricsQueryService(
                jdbcTemplate, aggregateApplicationService);
    }

    @Test
    void resolveAmountTrackLabel_shouldMapCreateTimeToEstimate() {
        assertThat(service.resolveAmountTrackLabel("createTime")).isEqualTo("estimate");
        assertThat(service.resolveAmountTrackLabel("settleTime")).isEqualTo("effective");
    }

    @Test
    void aggregateRange_shouldUseEstimateColumnsForCreateTrack() {
        when(jdbcTemplate.queryForMap(contains("co.estimate_service_fee"), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 2L,
                        "order_amount_cent", 5000L,
                        "service_fee_income_cent", 600L,
                        "tech_service_fee_cent", 60L,
                        "talent_commission_cent", 0L,
                        "service_profit_cent", 540L,
                        "recruiter_commission_cent", 54L,
                        "channel_commission_cent", 108L,
                        "gross_profit_cent", 378L));

        LocalDate today = LocalDate.now();
        PerformanceMetricsQueryService.PerformanceAggregate aggregate = service.aggregateRange(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "createTime",
                UUID.randomUUID(),
                null,
                DataScope.ALL);

        assertThat(aggregate.orderCount()).isEqualTo(2L);
        assertThat(aggregate.grossProfitCent()).isEqualTo(378L);
    }

    @Test
    void aggregateRange_shouldAlignCreateTrackWithAllOrderFacts() {
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

        LocalDate today = LocalDate.now();
        service.aggregateRange(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "createTime",
                UUID.randomUUID(),
                null,
                DataScope.ALL);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("FROM colonelsettlement_order co");
        assertThat(sql).contains("LEFT JOIN performance_records pr ON pr.order_id = co.order_id");
        assertThat(sql).contains("co.deleted = 0");
        assertThat(sql).doesNotContain("pr.is_valid = TRUE");
        assertThat(sql).contains("co.create_time >= ?");
        assertThat(sql).contains("co.create_time < ?");
        assertThat(sql).doesNotContain("co.pay_time");
    }

    @Test
    void trendByDay_shouldAlignCreateTrackWithAllOrderFacts() {
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "stat_date", LocalDate.now().toString(),
                        "order_count", 2L,
                        "order_amount_cent", 5000L)));

        LocalDate today = LocalDate.now();
        service.trendByDay(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "createTime",
                UUID.randomUUID(),
                null,
                DataScope.ALL);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("FROM colonelsettlement_order co");
        assertThat(sql).contains("LEFT JOIN performance_records pr ON pr.order_id = co.order_id");
        assertThat(sql).contains("COALESCE(SUM(co.order_amount), 0) AS order_amount_cent");
        assertThat(sql).doesNotContain("pr.is_valid = TRUE");
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
        service.aggregateRange(
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
                .contains("co.user_id = ?")
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
        service.aggregateRange(
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
                .contains("co.user_id = ?")
                .doesNotContain("co.dept_id = ?");
        assertThat(argsCaptor.getValue()[0]).isEqualTo(userId);
        verify(dataScopePolicy).decide(userId, deptId, DataScope.PERSONAL);
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
        service.aggregateRange(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                "settleTime",
                UUID.randomUUID(),
                null,
                DataScope.ALL);

        verify(jdbcTemplate).queryForMap(
                contains("settle_second_colonel_commission"),
                any(Object[].class));
    }

    @Test
    void aggregateDashboardSummary_shouldUseEffectiveTrackColumns() {
        when(jdbcTemplate.queryForMap(any(String.class), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 5L,
                        "order_amount_cent", 120000L,
                        "service_fee_cent", 2300L));
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "user_id", "channel-1",
                        "user_name", "渠道A",
                        "order_count", 2L,
                        "order_amount_cent", 50000L,
                        "service_fee_cent", 900L)))
                .thenReturn(List.of());

        PerformanceMetricsQueryService.DashboardPerformanceSummary summary = service.aggregateDashboardSummary(
                LocalDate.now().minusDays(7).atStartOfDay(),
                LocalDate.now().atTime(23, 59, 59),
                UUID.randomUUID(),
                null,
                DataScope.ALL);

        assertThat(summary.orderCount()).isEqualTo(5L);
        assertThat(summary.serviceFeeCent()).isEqualTo(2300L);
        assertThat(summary.channelPerformance()).hasSize(1);
        assertThat(summary.channelPerformance().get(0).userName()).isEqualTo("渠道A");
    }
}
