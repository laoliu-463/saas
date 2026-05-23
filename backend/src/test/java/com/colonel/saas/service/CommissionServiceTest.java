package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private CommissionRuleService commissionRuleService;
    @Mock
    private PerformanceCalculationService performanceCalculationService;

    private CommissionService commissionService;

    @BeforeEach
    void setUp() {
        commissionService = new CommissionService(jdbcTemplate, commissionRuleService, performanceCalculationService);
        org.mockito.Mockito.lenient()
                .when(commissionRuleService.resolveRatio(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
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
                new CommissionService.ActivityCommissionBucket("ACTIVITY_001", null, null, 10000L, 1000L, 2000L)
        ));

        assertThat(summary.serviceFeeNet()).isEqualTo(7000L);
        assertThat(summary.bizCommission()).isEqualTo(2100L);
        assertThat(summary.channelCommission()).isEqualTo(2800L);
        assertThat(summary.grossProfit()).isEqualTo(2100L);
    }

    @Test
    void calculate_shouldUseCommissionRuleRatiosBeforeLegacyActivityConfig() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), any()))
                .thenReturn("0.10")
                .thenReturn("0.20");
        when(commissionRuleService.resolveRatio(eq(CommissionRuleService.TYPE_RECRUITER), any(), any()))
                .thenReturn(new BigDecimal("0.25"));
        when(commissionRuleService.resolveRatio(eq(CommissionRuleService.TYPE_CHANNEL), any(), any()))
                .thenReturn(new BigDecimal("0.30"));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setActivityId("ACTIVITY_001");
        order.setProductId("PRODUCT_001");
        UUID recruiterId = UUID.randomUUID();
        order.setColonelUserId(recruiterId);
        order.setSettleColonelCommission(10000L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(order));

        assertThat(summary.bizCommission()).isEqualTo(2500L);
        assertThat(summary.channelCommission()).isEqualTo(3000L);
        assertThat(summary.bizRatio()).isEqualByComparingTo("0.25");
        assertThat(summary.channelRatio()).isEqualByComparingTo("0.30");

        ArgumentCaptor<CommissionRuleService.CommissionResolutionContext> contextCaptor =
                ArgumentCaptor.forClass(CommissionRuleService.CommissionResolutionContext.class);
        verify(commissionRuleService).resolveRatio(eq(CommissionRuleService.TYPE_RECRUITER), contextCaptor.capture(), any());
        assertThat(contextCaptor.getValue().activityId()).isEqualTo("ACTIVITY_001");
        assertThat(contextCaptor.getValue().productId()).isEqualTo("PRODUCT_001");
        assertThat(contextCaptor.getValue().recruiterUserId()).isEqualTo(recruiterId);
    }

    @Test
    void calculate_shouldFallbackWhenRatioQueryFails() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), any()))
                .thenThrow(new RuntimeException("db offline"));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setSettleColonelCommission(10000L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(order));

        assertThat(summary.bizRatio()).isEqualByComparingTo("0.15");
        assertThat(summary.channelRatio()).isEqualByComparingTo("0.15");
        assertThat(summary.bizCommission()).isEqualTo(1500L);
        assertThat(summary.channelCommission()).isEqualTo(1500L);
    }
}
