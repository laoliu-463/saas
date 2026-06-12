package com.colonel.saas.domain.performance.policy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-PERF-002: PerformanceMoneyPolicy 纯计算策略单测。
 */
class PerformanceMoneyPolicyTest {

    @Test
    void serviceFeeNetCent_shouldReturnIncomeMinusTechMinusExpense() {
        assertThat(PerformanceMoneyPolicy.serviceFeeNetCent(10000L, 1000L, 2000L))
                .isEqualTo(7000L);
    }

    @Test
    void serviceFeeNetCent_shouldFloorAtZero() {
        assertThat(PerformanceMoneyPolicy.serviceFeeNetCent(100L, 500L, 500L))
                .isEqualTo(0L);
    }

    @Test
    void activityServiceFeeNetCent_shouldComputeActivityNet() {
        // income=10000, expense=2000, tech=1000 → net=7000
        assertThat(PerformanceMoneyPolicy.activityServiceFeeNetCent(10000L, 2000L, 1000L))
                .isEqualTo(7000L);
    }

    @Test
    void multiplyCent_shouldRoundHalfUp() {
        // 1000 * 0.15 = 150
        assertThat(PerformanceMoneyPolicy.multiplyCent(1000L, new BigDecimal("0.15")))
                .isEqualTo(150L);
    }

    @Test
    void multiplyCent_shouldReturnZeroForNonPositiveAmount() {
        assertThat(PerformanceMoneyPolicy.multiplyCent(0L, new BigDecimal("0.15")))
                .isEqualTo(0L);
        assertThat(PerformanceMoneyPolicy.multiplyCent(-100L, new BigDecimal("0.15")))
                .isEqualTo(0L);
    }

    @Test
    void multiplyCent_shouldReturnZeroForNullRatio() {
        assertThat(PerformanceMoneyPolicy.multiplyCent(1000L, null))
                .isEqualTo(0L);
    }

    @Test
    void grossProfitCent_shouldReturnNetMinusCommissions() {
        // net=7000, biz=1050, channel=700 → profit=5250
        assertThat(PerformanceMoneyPolicy.grossProfitCent(7000L, 1050L, 700L))
                .isEqualTo(5250L);
    }

    @Test
    void grossProfitCent_shouldFloorAtZero() {
        assertThat(PerformanceMoneyPolicy.grossProfitCent(100L, 200L, 300L))
                .isEqualTo(0L);
    }

    @Test
    void calculate_shouldAggregateBucketsAndComputeCommissions() {
        // Two activity buckets with different ratios
        var bucket1 = new PerformanceMoneyPolicy.BucketInput(
                10000L, 1000L, 2000L, 500L,
                new BigDecimal("0.15"), new BigDecimal("0.10"));
        var bucket2 = new PerformanceMoneyPolicy.BucketInput(
                5000L, 500L, 1000L, 200L,
                new BigDecimal("0.20"), new BigDecimal("0.12"));

        var result = PerformanceMoneyPolicy.calculate(List.of(bucket1, bucket2));

        // total: income=15000, tech=1500, expense=3000, talent=700
        assertThat(result.serviceFeeIncome()).isEqualTo(15000L);
        assertThat(result.techServiceFee()).isEqualTo(1500L);
        assertThat(result.serviceFeeExpense()).isEqualTo(3000L);
        assertThat(result.talentCommission()).isEqualTo(700L);

        // serviceFeeNet = 15000 - 1500 - 3000 = 10500
        assertThat(result.serviceFeeNet()).isEqualTo(10500L);

        // bucket1 net = 10000 - 2000 - 1000 = 7000
        // bucket1 biz = 7000 * 0.15 = 1050, channel = 7000 * 0.10 = 700
        // bucket2 net = 5000 - 1000 - 500 = 3500
        // bucket2 biz = 3500 * 0.20 = 700, channel = 3500 * 0.12 = 420
        // total biz = 1750, channel = 1120
        assertThat(result.bizCommission()).isEqualTo(1750L);
        assertThat(result.channelCommission()).isEqualTo(1120L);

        // grossProfit = 10500 - 1750 - 1120 = 7630
        assertThat(result.grossProfit()).isEqualTo(7630L);

        // last ratios from bucket2
        assertThat(result.lastBizRatio()).isEqualByComparingTo("0.20");
        assertThat(result.lastChannelRatio()).isEqualByComparingTo("0.12");
    }

    @Test
    void calculate_shouldReturnEmptyResultForNullInput() {
        var result = PerformanceMoneyPolicy.calculate(null);
        assertThat(result.serviceFeeNet()).isEqualTo(0L);
        assertThat(result.bizCommission()).isEqualTo(0L);
        assertThat(result.grossProfit()).isEqualTo(0L);
    }

    @Test
    void calculate_shouldReturnEmptyResultForEmptyList() {
        var result = PerformanceMoneyPolicy.calculate(List.of());
        assertThat(result.serviceFeeNet()).isEqualTo(0L);
    }

    @Test
    void serviceFeeNetCent_shouldMatchCommissionServiceFormula() {
        // Verify the formula matches CommissionService.serviceFeeNetCent:
        // income=499563, tech=100000, expense=100000 → net=299563
        assertThat(PerformanceMoneyPolicy.serviceFeeNetCent(499563L, 100000L, 100000L))
                .isEqualTo(299563L);
    }
}
