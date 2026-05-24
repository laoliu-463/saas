package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceTrackSummaryDTO;
import com.colonel.saas.service.performance.PerformanceAccessContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceSummaryServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PerformanceSummaryService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceSummaryService(jdbcTemplate);
    }

    @Test
    void aggregateEstimate_shouldUseEstimateColumns() {
        when(jdbcTemplate.queryForMap(contains("pr.estimate_service_fee"), any(Object[].class)))
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
        assertThat(track.getServiceFeeExpense()).isEqualTo(81L);
        assertThat(track.getGrossProfit()).isEqualTo(459L);
    }

    @Test
    void aggregateEffective_shouldUseEffectiveColumns() {
        when(jdbcTemplate.queryForMap(contains("pr.effective_service_fee"), any(Object[].class)))
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
}
