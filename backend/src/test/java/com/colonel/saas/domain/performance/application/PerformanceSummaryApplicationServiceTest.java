package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.dto.performance.PerformanceTrackSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerformanceSummaryApplicationService 直接行为验证（DDD-PERFORMANCE Slice 4）。
 *
 * <p>核心目标：验证 Application 层独立持有完整的卡片汇总 SQL 装配逻辑，
 * 不依赖 Service 中转。Service 层委派是 thin shell，
 * 真实业务逻辑必须由本测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class PerformanceSummaryApplicationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PerformanceSummaryApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new PerformanceSummaryApplicationService(
                jdbcTemplate,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()));
    }

    private PerformanceAccessContext personalStaffScope(UUID userId) {
        return new PerformanceAccessContext(userId, null, com.colonel.saas.common.enums.DataScope.PERSONAL,
                java.util.List.of("CHANNEL_STAFF"));
    }

    private PerformanceSummaryQuery emptyQuery() {
        return new PerformanceSummaryQuery();
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

        PerformanceSummaryQuery query = emptyQuery();
        query.setTimeFilterType("pay");
        query.setTimeStart(LocalDateTime.of(2026, 5, 1, 0, 0));
        query.setTimeEnd(LocalDateTime.of(2026, 6, 1, 0, 0));

        PerformanceTrackSummaryDTO result = applicationService.aggregateEstimate(query, personalStaffScope(UUID.randomUUID()));

        assertThat(result.getOrderCount()).isEqualTo(3L);
        assertThat(result.getOrderAmount()).isEqualTo(9000L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("co.order_amount")
                .contains("co.estimate_service_fee")
                .contains("pr.estimate_service_profit")
                .contains("co.order_status IS NULL OR co.order_status NOT IN (4, 5)")
                .contains("COALESCE(pr.is_valid, true) = true")
                .contains("COALESCE(pr.is_reversed, false) = false")
                .doesNotContain("co.settle_amount")
                .doesNotContain("co.effective_service_fee");
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

        PerformanceSummaryQuery query = emptyQuery();
        query.setTimeFilterType("settle");
        query.setTimeStart(LocalDateTime.of(2026, 5, 1, 0, 0));
        query.setTimeEnd(LocalDateTime.of(2026, 6, 1, 0, 0));

        PerformanceTrackSummaryDTO result = applicationService.aggregateEffective(query, personalStaffScope(UUID.randomUUID()));

        assertThat(result.getOrderCount()).isEqualTo(2L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("co.settle_amount")
                .contains("co.effective_service_fee")
                .contains("pr.effective_gross_profit")
                .contains("co.order_status IS NULL OR co.order_status NOT IN (4, 5)")
                .contains("COALESCE(pr.is_valid, true) = true")
                .contains("COALESCE(pr.is_reversed, false) = false")
                .contains("(co.settle_time IS NOT NULL OR co.effective_service_fee > 0)")
                .doesNotContain("co.estimate_service_fee");
    }

    @Test
    void getSummary_shouldComposeEstimateAndEffectiveTracks() {
        when(jdbcTemplate.queryForMap(any(String.class), any(Object[].class)))
                .thenReturn(Map.of(
                        "order_count", 5L,
                        "order_amount", 10000L,
                        "service_fee_income", 800L,
                        "tech_service_fee", 80L,
                        "service_fee_profit", 720L,
                        "recruiter_commission", 72L,
                        "channel_commission", 36L,
                        "gross_profit", 612L));

        PerformanceSummaryResponse response = applicationService.getSummary(emptyQuery(), personalStaffScope(UUID.randomUUID()));

        assertThat(response.getEstimate()).isNotNull();
        assertThat(response.getEffective()).isNotNull();
        // getSummary 调用两次 jdbcTemplate.queryForMap（estimate + effective）
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .queryForMap(any(String.class), any(Object[].class));
    }
}
