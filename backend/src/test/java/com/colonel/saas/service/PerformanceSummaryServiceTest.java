package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceTrackSummaryDTO;
import com.colonel.saas.domain.performance.application.PerformanceSummaryApplicationService;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceSummaryServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PerformanceSummaryService service;

    @BeforeEach
    void setUp() {
        // DDD-PERFORMANCE Slice 4: getSummary/aggregateEstimate/aggregateEffective
        // 已下沉至 application 层；service 是 thin shell 委派壳。
        // 测试使用同一份 mock jdbcTemplate 共享给 Application，保证 SQL 装配行为可被验证。
        PerformanceSummaryApplicationService applicationService = new PerformanceSummaryApplicationService(jdbcTemplate);
        service = new PerformanceSummaryService(applicationService);
    }

    @Test
    void aggregateEstimate_shouldUseEstimateColumns() {
        when(jdbcTemplate.queryForMap(contains("co.estimate_service_fee"), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 3L,
                        "order_amount", 9000L,
                        "service_fee_income", 600L,
                        "tech_service_fee", 60L,
                        "service_fee_profit", 540L,
                        "recruiter_commission", 54L,
                        "channel_commission", 27L,
                        "gross_profit", 459L));

        PerformanceSummaryQuery query = new PerformanceSummaryQuery();
        query.setTimeFilterType("pay");
        query.setTimeStart(LocalDateTime.of(2026, 5, 1, 0, 0));
        query.setTimeEnd(LocalDateTime.of(2026, 6, 1, 0, 0));

        PerformanceTrackSummaryDTO track = service.aggregateEstimate(
                query,
                PerformanceAccessContext.of(null, null, DataScope.ALL, java.util.List.of("admin")));

        assertThat(track.getOrderCount()).isEqualTo(3L);
        assertThat(track.getOrderAmount()).isEqualTo(9000L);
        assertThat(track.getServiceFeeExpense()).isEqualTo(0L);
        assertThat(track.getServiceFeeProfit()).isEqualTo(540L);
        assertThat(track.getGrossProfit()).isEqualTo(459L);
    }

    @Test
    void aggregateEffective_shouldUseEffectiveColumns() {
        when(jdbcTemplate.queryForMap(contains("co.effective_service_fee"), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 2L,
                        "order_amount", 5000L,
                        "service_fee_income", 400L,
                        "tech_service_fee", 40L,
                        "service_fee_profit", 360L,
                        "recruiter_commission", 36L,
                        "channel_commission", 18L,
                        "gross_profit", 306L));

        PerformanceTrackSummaryDTO track = service.aggregateEffective(
                new PerformanceSummaryQuery(),
                PerformanceAccessContext.of(null, null, DataScope.ALL, java.util.List.of("admin")));

        assertThat(track.getOrderCount()).isEqualTo(2L);
        assertThat(track.getServiceFeeIncome()).isEqualTo(400L);
    }

    @Test
    void aggregateEffective_shouldUseExpenseDirectlyFromDB() {
        // 服务费支出直接从 DB 取值，不使用反推公式
        when(jdbcTemplate.queryForMap(contains("co.effective_service_fee"), any(Object[].class)))
                .thenReturn(summaryRowWithExpense(2L, 5000L, 400L, 40L, 5L, 390L, 39L, 19L, 332L));

        PerformanceTrackSummaryDTO track = service.aggregateEffective(
                new PerformanceSummaryQuery(),
                PerformanceAccessContext.of(null, null, DataScope.ALL, java.util.List.of("admin")));

        // DB 中 service_fee_expense = 5，直接返回
        assertThat(track.getServiceFeeExpense()).isEqualTo(5L);
    }

    @Test
    void aggregateEffective_shouldNotDeductTechServiceFeeFromSettlementProfit() {
        when(jdbcTemplate.queryForMap(contains("co.effective_service_fee"), any(Object[].class)))
                .thenReturn(summaryRowWithExpense(2L, 5000L, 400L, 40L, 5L, 390L, 39L, 19L, 332L));

        PerformanceTrackSummaryDTO track = service.aggregateEffective(
                new PerformanceSummaryQuery(),
                PerformanceAccessContext.of(null, null, DataScope.ALL, java.util.List.of("admin")));

        assertThat(track.getServiceFeeProfit()).isEqualTo(395L);
        assertThat(track.getGrossProfit()).isEqualTo(337L);
    }

    @Test
    void getSummary_shouldUseSafeEmptyQueryAndMapInvalidNumbers() {
        when(jdbcTemplate.queryForMap(contains("co.estimate_service_fee"), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", "bad-number",
                        "service_fee_income", "1200",
                        "tech_service_fee", 80L,
                        "service_fee_profit", "bad-profit",
                        "recruiter_commission", 30L,
                        "channel_commission", 12L,
                        "gross_profit", 99L));
        when(jdbcTemplate.queryForMap(contains("co.effective_service_fee"), any(Object[].class)))
                .thenReturn(summaryRow(1L, 2000L, 160L, 16L, 144L, 14L, 7L, 123L));

        var summary = service.getSummary(
                null,
                PerformanceAccessContext.of(null, null, DataScope.ALL, List.of(RoleCodes.ADMIN)));

        assertThat(summary.getEstimate().getOrderCount()).isZero();
        assertThat(summary.getEstimate().getOrderAmount()).isZero();
        assertThat(summary.getEstimate().getServiceFeeIncome()).isEqualTo(1200L);
        assertThat(summary.getEstimate().getServiceFeeProfit()).isEqualTo(1120L);
        assertThat(summary.getEstimate().getServiceFeeExpense()).isZero();
        assertThat(summary.getEffective().getGrossProfit()).isEqualTo(139L);
    }

    @Test
    void aggregateEstimate_shouldTrimFiltersMapStatusAndUsePayCohort() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 0, 0);
        UUID talentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PerformanceSummaryQuery query = new PerformanceSummaryQuery();
        query.setActivityId(" ACT-1 ");
        query.setProductId(" P-1 ");
        query.setPartnerId(9L);
        query.setTalentId(talentId);
        query.setOrderStatus(" shipped ");
        query.setTimeFilterType("pay");
        query.setTimeStart(start);
        query.setTimeEnd(end);
        when(jdbcTemplate.queryForMap(contains("co.estimate_service_fee"), any(Object[].class)))
                .thenReturn(summaryRow(1L, 100L, 10L, 1L, 9L, 2L, 3L, 4L));

        service.aggregateEstimate(
                query,
                PerformanceAccessContext.of(null, null, DataScope.ALL, List.of(RoleCodes.ADMIN)));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("FROM colonelsettlement_order co")
                .contains("LEFT JOIN performance_records pr ON pr.order_id = co.order_id")
                .contains("co.deleted = 0")
                .doesNotContain("pr.is_valid = TRUE")
                .contains("co.colonel_activity_id = ?")
                .contains("co.product_id = ?")
                .contains("pr.partner_id = ?")
                .contains("co.talent_id = ?")
                .contains("co.order_status = ?")
                .contains("COALESCE(co.order_create_time, co.create_time) >= ?")
                .contains("COALESCE(co.order_create_time, co.create_time) < ?")
                .doesNotContain("AND co.settle_time IS NOT NULL");
        assertThat(argsCaptor.getValue())
                .containsExactly("ACT-1", "P-1", 9L, talentId, 2, start, end);
    }

    @Test
    void aggregateEffective_shouldUseSettleCohortAndRequireSettledRows() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 0, 0);
        PerformanceSummaryQuery query = new PerformanceSummaryQuery();
        query.setTimeFilterType("settle");
        query.setTimeStart(start);
        query.setTimeEnd(end);
        when(jdbcTemplate.queryForMap(contains("co.effective_service_fee"), any(Object[].class)))
                .thenReturn(summaryRow(1L, 100L, 10L, 1L, 9L, 2L, 3L, 4L));

        service.aggregateEffective(
                query,
                PerformanceAccessContext.of(null, null, DataScope.ALL, List.of(RoleCodes.ADMIN)));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("co.settle_time >= ?")
                .contains("co.settle_time < ?")
                .contains("AND co.settle_time IS NOT NULL")
                .contains("AND (co.settle_time IS NOT NULL OR co.effective_service_fee > 0)");
        assertThat(argsCaptor.getValue()).containsExactly(start, end);
    }

    @Test
    void aggregateEstimate_shouldApplyChannelStaffScopeBeforeSummaryFilters() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PerformanceSummaryQuery query = new PerformanceSummaryQuery();
        query.setChannelId(userId);
        when(jdbcTemplate.queryForMap(contains("co.estimate_service_fee"), any(Object[].class)))
                .thenReturn(summaryRow(1L, 100L, 10L, 1L, 9L, 2L, 3L, 4L));

        service.aggregateEstimate(
                query,
                PerformanceAccessContext.of(userId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("pr.final_channel_user_id = ?")
                .contains("AND pr.final_channel_user_id = ?");
        assertThat(argsCaptor.getValue()).containsExactly(userId, userId);
    }

    @Test
    void getSummary_shouldRejectCrossChannelFilterForChannelStaff() {
        PerformanceSummaryQuery query = new PerformanceSummaryQuery();
        query.setChannelId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        PerformanceAccessContext context = PerformanceAccessContext.of(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF));

        assertThatThrownBy(() -> service.getSummary(query, context))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbcTemplate);
    }

    private static Map<String, Object> summaryRow(
            long orderCount,
            long orderAmount,
            long serviceFeeIncome,
            long techServiceFee,
            long serviceFeeProfit,
            long recruiterCommission,
            long channelCommission,
            long grossProfit) {
        return Map.of(
                "order_count", orderCount,
                "order_amount", orderAmount,
                "service_fee_income", serviceFeeIncome,
                "tech_service_fee", techServiceFee,
                "service_fee_profit", serviceFeeProfit,
                "recruiter_commission", recruiterCommission,
                "channel_commission", channelCommission,
                "gross_profit", grossProfit);
    }

    private static Map<String, Object> summaryRowWithExpense(
            long orderCount,
            long orderAmount,
            long serviceFeeIncome,
            long techServiceFee,
            long serviceFeeExpense,
            long serviceFeeProfit,
            long recruiterCommission,
            long channelCommission,
            long grossProfit) {
        return Map.of(
                "order_count", orderCount,
                "order_amount", orderAmount,
                "service_fee_income", serviceFeeIncome,
                "tech_service_fee", techServiceFee,
                "service_fee_expense", serviceFeeExpense,
                "service_fee_profit", serviceFeeProfit,
                "recruiter_commission", recruiterCommission,
                "channel_commission", channelCommission,
                "gross_profit", grossProfit);
    }
}
