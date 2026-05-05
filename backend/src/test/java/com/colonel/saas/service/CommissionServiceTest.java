package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CommissionService commissionService;

    @BeforeEach
    void setUp() {
        commissionService = new CommissionService(jdbcTemplate);
    }

    @Test
    void calculate_shouldUseConfigRatios() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), any()))
                .thenReturn("0.10")
                .thenReturn("0.20");

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setSettleColonelCommission(10000L);
        order.setSettleColonelTechServiceFee(1000L);
        order.setSettleSecondColonelCommission(2000L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(order));

        assertThat(summary.serviceFeeIncome()).isEqualTo(10000L);
        assertThat(summary.techServiceFee()).isEqualTo(1000L);
        assertThat(summary.talentCommission()).isEqualTo(2000L);
        assertThat(summary.serviceFeeNet()).isEqualTo(7000L);
        assertThat(summary.bizCommission()).isEqualTo(700L);
        assertThat(summary.channelCommission()).isEqualTo(1400L);
        assertThat(summary.grossProfit()).isEqualTo(4900L);
    }

    @Test
    void calculate_shouldFallbackWhenConfigMissing() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), any()))
                .thenReturn(null)
                .thenReturn(null);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setSettleColonelCommission(10000L);
        order.setSettleColonelTechServiceFee(0L);
        order.setSettleSecondColonelCommission(0L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(order));
        assertThat(summary.bizCommission()).isEqualTo(1500L);
        assertThat(summary.channelCommission()).isEqualTo(1500L);
    }

    @Test
    void calculate_shouldUseActivitySpecificRatiosWhenConfigured() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), any()))
                .thenReturn("0.10")
                .thenReturn("0.20")
                .thenReturn("0.30")
                .thenReturn("0.40");

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setActivityId("ACTIVITY_001");
        order.setSettleColonelCommission(10000L);
        order.setSettleColonelTechServiceFee(1000L);
        order.setSettleSecondColonelCommission(2000L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(order));

        assertThat(summary.serviceFeeNet()).isEqualTo(7000L);
        assertThat(summary.bizCommission()).isEqualTo(2100L);
        assertThat(summary.channelCommission()).isEqualTo(2800L);
        assertThat(summary.grossProfit()).isEqualTo(2100L);
    }

    @Test
    void calculate_shouldMixDefaultAndActivitySpecificRatiosByActivity() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), any()))
                .thenReturn("0.10")
                .thenReturn("0.20")
                .thenReturn("0.30")
                .thenReturn("0.40")
                .thenReturn(null)
                .thenReturn(null);

        ColonelsettlementOrder activityOrder = new ColonelsettlementOrder();
        activityOrder.setActivityId("ACTIVITY_001");
        activityOrder.setSettleColonelCommission(10000L);
        activityOrder.setSettleColonelTechServiceFee(1000L);
        activityOrder.setSettleSecondColonelCommission(2000L);

        ColonelsettlementOrder defaultOrder = new ColonelsettlementOrder();
        defaultOrder.setActivityId("ACTIVITY_002");
        defaultOrder.setSettleColonelCommission(8000L);
        defaultOrder.setSettleColonelTechServiceFee(1000L);
        defaultOrder.setSettleSecondColonelCommission(1000L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(activityOrder, defaultOrder));

        assertThat(summary.serviceFeeNet()).isEqualTo(13000L);
        assertThat(summary.bizCommission()).isEqualTo(2700L);
        assertThat(summary.channelCommission()).isEqualTo(4000L);
        assertThat(summary.grossProfit()).isEqualTo(6300L);
    }

    @Test
    void calculateByActivityBuckets_shouldUseAggregatedRows() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), any()))
                .thenReturn("0.10")
                .thenReturn("0.20")
                .thenReturn("0.30")
                .thenReturn("0.40");

        CommissionService.CommissionSummary summary = commissionService.calculateByActivityBuckets(List.of(
                new CommissionService.ActivityCommissionBucket("ACTIVITY_001", 10000L, 1000L, 2000L)
        ));

        assertThat(summary.serviceFeeNet()).isEqualTo(7000L);
        assertThat(summary.bizCommission()).isEqualTo(2100L);
        assertThat(summary.channelCommission()).isEqualTo(2800L);
        assertThat(summary.grossProfit()).isEqualTo(2100L);
    }

    @Test
    void calculate_shouldThrowWhenRatioQueryFails() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), any()))
                .thenThrow(new RuntimeException("db offline"));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setSettleColonelCommission(10000L);

        assertThatThrownBy(() -> commissionService.calculate(List.of(order)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commission.business_default_ratio");
    }
}
