package com.colonel.saas.service;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    @Mock
    private ConfigDomainFacade configDomainFacade;
    @Mock
    private CommissionRuleService commissionRuleService;
    @Mock
    private PerformanceCalculationService performanceCalculationService;

    private CommissionService commissionService;

    @BeforeEach
    void setUp() {
        commissionService = new CommissionService(configDomainFacade, commissionRuleService, performanceCalculationService);
        org.mockito.Mockito.lenient()
                .when(commissionRuleService.resolveRule(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
    }

    @Test
    void calculate_shouldUseConfigRatios() {
        stubDefaultRatios("0.10", "0.20");

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setSettleColonelCommission(10000L);
        order.setSettleColonelTechServiceFee(1000L);
        order.setSettleSecondColonelCommission(2000L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(order));

        assertThat(summary.serviceFeeIncome()).isEqualTo(10000L);
        assertThat(summary.techServiceFee()).isEqualTo(1000L);
        assertThat(summary.talentCommission()).isEqualTo(2000L);
        assertThat(summary.serviceFeeNet()).isEqualTo(9000L);
        assertThat(summary.bizCommission()).isEqualTo(900L);
        assertThat(summary.channelCommission()).isEqualTo(1800L);
        assertThat(summary.grossProfit()).isEqualTo(6300L);
    }

    @Test
    void calculate_shouldFallbackWhenConfigMissing() {
        when(configDomainFacade.getDecimal(any(), any())).thenReturn(null);

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
        stubDefaultRatios("0.10", "0.20");
        when(configDomainFacade.getConfig("commission.business_activity_ratio.ACTIVITY_001")).thenReturn("0.30");
        when(configDomainFacade.getConfig("commission.channel_activity_ratio.ACTIVITY_001")).thenReturn("0.40");

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setActivityId("ACTIVITY_001");
        order.setSettleColonelCommission(10000L);
        order.setSettleColonelTechServiceFee(1000L);
        order.setSettleSecondColonelCommission(2000L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(order));

        assertThat(summary.serviceFeeNet()).isEqualTo(9000L);
        assertThat(summary.bizCommission()).isEqualTo(2700L);
        assertThat(summary.channelCommission()).isEqualTo(3600L);
        assertThat(summary.grossProfit()).isEqualTo(2700L);
    }

    @Test
    void calculate_shouldMixDefaultAndActivitySpecificRatiosByActivity() {
        stubDefaultRatios("0.10", "0.20");
        when(configDomainFacade.getConfig("commission.business_activity_ratio.ACTIVITY_001")).thenReturn("0.30");
        when(configDomainFacade.getConfig("commission.channel_activity_ratio.ACTIVITY_001")).thenReturn("0.40");
        when(configDomainFacade.getConfig("commission.business_activity_ratio.ACTIVITY_002")).thenReturn(null);
        when(configDomainFacade.getConfig("commission.channel_activity_ratio.ACTIVITY_002")).thenReturn(null);

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

        assertThat(summary.serviceFeeNet()).isEqualTo(16000L);
        assertThat(summary.bizCommission()).isEqualTo(3400L);
        assertThat(summary.channelCommission()).isEqualTo(5000L);
        assertThat(summary.grossProfit()).isEqualTo(7600L);
    }

    @Test
    void calculateByActivityBuckets_shouldUseAggregatedRows() {
        stubDefaultRatios("0.10", "0.20");
        when(configDomainFacade.getConfig("commission.business_activity_ratio.ACTIVITY_001")).thenReturn("0.30");
        when(configDomainFacade.getConfig("commission.channel_activity_ratio.ACTIVITY_001")).thenReturn("0.40");

        CommissionService.CommissionSummary summary = commissionService.calculateByActivityBuckets(List.of(
                new CommissionService.ActivityCommissionBucket("ACTIVITY_001", null, null, 10000L, 1000L, 2000L)
        ));

        assertThat(summary.serviceFeeNet()).isEqualTo(9000L);
        assertThat(summary.bizCommission()).isEqualTo(2700L);
        assertThat(summary.channelCommission()).isEqualTo(3600L);
        assertThat(summary.grossProfit()).isEqualTo(2700L);
    }

    @Test
    void calculate_shouldUseCommissionRuleRatiosBeforeLegacyActivityConfig() {
        stubDefaultRatios("0.10", "0.20");
        UUID bizRuleId = UUID.randomUUID();
        UUID channelRuleId = UUID.randomUUID();
        when(commissionRuleService.resolveRule(eq(CommissionRuleService.TYPE_RECRUITER), any(), any()))
                .thenReturn(new CommissionRuleService.CommissionRuleResolution(
                        new BigDecimal("0.25"),
                        bizRuleId,
                        6,
                        LocalDateTime.of(2026, 7, 8, 10, 0),
                        CommissionRuleService.DIMENSION_PRODUCT,
                        "PRODUCT_001",
                        CommissionRuleService.TYPE_RECRUITER));
        when(commissionRuleService.resolveRule(eq(CommissionRuleService.TYPE_CHANNEL), any(), any()))
                .thenReturn(new CommissionRuleService.CommissionRuleResolution(
                        new BigDecimal("0.30"),
                        channelRuleId,
                        8,
                        LocalDateTime.of(2026, 7, 8, 11, 0),
                        CommissionRuleService.DIMENSION_ACTIVITY,
                        "ACTIVITY_001",
                        CommissionRuleService.TYPE_CHANNEL));

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
        assertThat(summary.bizRatioSource()).isEqualTo("commission_rule");
        assertThat(summary.bizRatioSourceKey()).isEqualTo("product:PRODUCT_001");
        assertThat(summary.bizRatioRuleId()).isEqualTo(bizRuleId);
        assertThat(summary.bizRatioSourceVersion()).isEqualTo("6");
        assertThat(summary.channelRatioSource()).isEqualTo("commission_rule");
        assertThat(summary.channelRatioSourceKey()).isEqualTo("activity:ACTIVITY_001");
        assertThat(summary.channelRatioRuleId()).isEqualTo(channelRuleId);
        assertThat(summary.channelRatioSourceVersion()).isEqualTo("8");

        ArgumentCaptor<CommissionRuleService.CommissionResolutionContext> contextCaptor =
                ArgumentCaptor.forClass(CommissionRuleService.CommissionResolutionContext.class);
        verify(commissionRuleService).resolveRule(eq(CommissionRuleService.TYPE_RECRUITER), contextCaptor.capture(), any());
        assertThat(contextCaptor.getValue().activityId()).isEqualTo("ACTIVITY_001");
        assertThat(contextCaptor.getValue().productId()).isEqualTo("PRODUCT_001");
        assertThat(contextCaptor.getValue().recruiterUserId()).isEqualTo(recruiterId);
    }

    @Test
    void calculate_shouldFallbackWhenRatioQueryFails() {
        when(configDomainFacade.getDecimal(any(), any())).thenThrow(new RuntimeException("config offline"));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setSettleColonelCommission(10000L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(order));

        assertThat(summary.bizRatio()).isEqualByComparingTo("0.15");
        assertThat(summary.channelRatio()).isEqualByComparingTo("0.15");
        assertThat(summary.bizCommission()).isEqualTo(1500L);
        assertThat(summary.channelCommission()).isEqualTo(1500L);
    }

    private void stubDefaultRatios(String businessRatio, String channelRatio) {
        when(configDomainFacade.getDecimal(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO, new BigDecimal("0.15")))
                .thenReturn(new BigDecimal(businessRatio));
        when(configDomainFacade.getDecimal(SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO, new BigDecimal("0.15")))
                .thenReturn(new BigDecimal(channelRatio));
    }
}
