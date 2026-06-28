package com.colonel.saas.service;

import com.colonel.saas.domain.order.policy.OrderCommissionPolicy;
import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.mapper.PerformanceRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceCalculationServiceTest {

    @Mock
    private PerformanceRecordMapper performanceRecordMapper;
    @Mock
    private ConfigDomainFacade configDomainFacade;
    @Mock
    private CommissionRuleService commissionRuleService;

    private PerformanceCalculationService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceCalculationService(
                performanceRecordMapper,
                new CommissionService(configDomainFacade, commissionRuleService, null));
        lenient().when(commissionRuleService.resolveRatio(any(), any(), any())).thenReturn(null);
        lenient().when(configDomainFacade.getDecimal(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO, new BigDecimal("0.15")))
                .thenReturn(new BigDecimal("0.10"));
        lenient().when(configDomainFacade.getDecimal(SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO, new BigDecimal("0.15")))
                .thenReturn(new BigDecimal("0.20"));
        lenient().when(configDomainFacade.getConfig(anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    if (key != null && key.contains("business")) {
                        return "0.10";
                    }
                    if (key != null && key.contains("channel")) {
                        return "0.20";
                    }
                    return null;
                });
    }

    @Test
    void upsertFromOrder_shouldCalculateDualTrackCommissions() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-100");
        order.setActivityId("ACT-1");
        order.setOrderAmount(10000L);
        order.setSettleAmount(10000L);
        order.setEstimateServiceFee(1000L);
        order.setEffectiveServiceFee(900L);
        order.setEstimateTechServiceFee(100L);
        order.setEffectiveTechServiceFee(90L);
        order.setSettleSecondColonelCommission(50L);
        order.setOrderStatus(1);

        when(performanceRecordMapper.findByOrderId("ORD-100")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord saved = service.upsertFromOrder(order);

        ArgumentCaptor<PerformanceRecord> captor = ArgumentCaptor.forClass(PerformanceRecord.class);
        verify(performanceRecordMapper).upsert(captor.capture());
        PerformanceRecord record = captor.getValue();

        assertThat(saved).isNotNull();
        assertThat(record.getEstimateServiceProfit()).isEqualTo(900L);
        assertThat(record.getEffectiveServiceProfit()).isEqualTo(900L);
        assertThat(record.getEstimateRecruiterCommission()).isEqualTo(90L);
        assertThat(record.getEffectiveRecruiterCommission()).isEqualTo(90L);
        assertThat(record.getEstimateChannelCommission()).isEqualTo(180L);
        assertThat(record.getEffectiveChannelCommission()).isEqualTo(180L);
        assertThat(record.getEstimateGrossProfit()).isEqualTo(630L);
        assertThat(record.getEffectiveGrossProfit()).isEqualTo(630L);
    }

    @Test
    void upsertFromOrder_shouldNotDeductTechServiceFeeAgainFromEffectiveIncome() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-EFFECTIVE-NET");
        order.setActivityId("ACT-1");
        order.setOrderAmount(10000L);
        order.setSettleAmount(8000L);
        order.setEstimateServiceFee(1000L);
        order.setEffectiveServiceFee(720L);
        order.setEstimateTechServiceFee(100L);
        order.setEffectiveTechServiceFee(80L);
        order.setOrderStatus(1);

        when(performanceRecordMapper.findByOrderId("ORD-EFFECTIVE-NET")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        service.upsertFromOrder(order);

        ArgumentCaptor<PerformanceRecord> captor = ArgumentCaptor.forClass(PerformanceRecord.class);
        verify(performanceRecordMapper).upsert(captor.capture());
        PerformanceRecord record = captor.getValue();

        assertThat(record.getEstimateServiceProfit()).isEqualTo(900L);
        assertThat(record.getEffectiveServiceProfit()).isEqualTo(720L);
        assertThat(record.getEffectiveRecruiterCommission()).isEqualTo(72L);
        assertThat(record.getEffectiveChannelCommission()).isEqualTo(144L);
        assertThat(record.getEffectiveGrossProfit()).isEqualTo(504L);
    }

    @Test
    void upsertFromOrder_shouldReverseCancelledOrders() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD-CANCEL");
        order.setOrderStatus(OrderCommissionPolicy.STATUS_CANCELLED);
        order.setEstimateServiceFee(1000L);

        when(performanceRecordMapper.findByOrderId("ORD-CANCEL")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord saved = service.upsertFromOrder(order);

        assertThat(saved.getReversed()).isTrue();
        assertThat(saved.getValid()).isFalse();
        assertThat(saved.getEstimateGrossProfit()).isZero();
        assertThat(saved.getEffectiveGrossProfit()).isZero();
    }

    @Test
    void upsertFromOrder_shouldReverseRefundedOrders() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD-REFUND");
        order.setOrderStatus(OrderCommissionPolicy.STATUS_REFUNDED);
        order.setEstimateServiceFee(800L);

        when(performanceRecordMapper.findByOrderId("ORD-REFUND")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord saved = service.upsertFromOrder(order);

        assertThat(saved.getReversed()).isTrue();
        assertThat(saved.getValid()).isFalse();
        assertThat(saved.getEstimateRecruiterCommission()).isZero();
        assertThat(saved.getEstimateChannelCommission()).isZero();
        assertThat(saved.getEstimateGrossProfit()).isZero();
    }

    @Test
    void upsertFromOrder_shouldPreserveExistingRecordAndKeepUnsettledAmountZero() {
        UUID existingId = UUID.randomUUID();
        UUID orderRowId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 1, 9, 0);
        PerformanceRecord existing = new PerformanceRecord();
        existing.setId(existingId);
        existing.setCreatedAt(createdAt);
        existing.setCalculationVersion(3);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(orderRowId);
        order.setOrderId("ORD-RECALC");
        order.setUserId(recruiterId);
        order.setOrderStatus(1);
        order.setOrderAmount(15000L);
        order.setSettleAmount(0L);
        order.setActualAmount(12300L);
        order.setEstimateServiceFee(0L);
        order.setEffectiveServiceFee(0L);
        order.setEstimateTechServiceFee(0L);
        order.setEffectiveTechServiceFee(0L);

        when(performanceRecordMapper.findByOrderId("ORD-RECALC")).thenReturn(existing);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord saved = service.upsertFromOrder(order);

        ArgumentCaptor<PerformanceRecord> captor = ArgumentCaptor.forClass(PerformanceRecord.class);
        verify(performanceRecordMapper).upsert(captor.capture());
        PerformanceRecord record = captor.getValue();
        assertThat(saved).isSameAs(record);
        assertThat(record.getId()).isEqualTo(existingId);
        assertThat(record.getOrderRowId()).isEqualTo(orderRowId);
        assertThat(record.getCreatedAt()).isEqualTo(createdAt);
        assertThat(record.getCalculationVersion()).isEqualTo(4);
        assertThat(record.getPayAmount()).isEqualTo(15000L);
        assertThat(record.getSettleAmount()).isZero();
        assertThat(record.getDefaultChannelUserId()).isNull();
        assertThat(record.getFinalChannelUserId()).isNull();
        assertThat(record.getChannelAttribution()).isEqualTo("unattributed");
        assertThat(record.getDefaultRecruiterUserId()).isEqualTo(recruiterId);
        assertThat(record.getFinalRecruiterUserId()).isEqualTo(recruiterId);
        assertThat(record.getRecruiterAttribution()).isEqualTo("activity_owner");
        assertThat(record.getValid()).isTrue();
        assertThat(record.getReversed()).isFalse();
    }

    @Test
    void upsertFromOrder_shouldUseCustomRatioFromCommissionRuleService() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-CUSTOM-RATIO");
        order.setActivityId("ACT-CUSTOM");
        order.setOrderAmount(10000L);
        order.setSettleAmount(10000L);
        order.setEstimateServiceFee(1000L);
        order.setEffectiveServiceFee(1000L);
        order.setOrderStatus(1);

        when(performanceRecordMapper.findByOrderId("ORD-CUSTOM-RATIO")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        // Mock custom ratios
        when(commissionRuleService.resolveRatio(
                org.mockito.ArgumentMatchers.eq(CommissionRuleService.TYPE_RECRUITER),
                any(),
                any()))
                .thenReturn(new BigDecimal("0.25"));
        when(commissionRuleService.resolveRatio(
                org.mockito.ArgumentMatchers.eq(CommissionRuleService.TYPE_CHANNEL),
                any(),
                any()))
                .thenReturn(new BigDecimal("0.35"));

        PerformanceRecord saved = service.upsertFromOrder(order);

        assertThat(saved).isNotNull();
        // Base profit = 1000L (EstimateServiceFee)
        // Recruiter commission = 1000L * 0.25 = 250L
        // Channel commission = 1000L * 0.35 = 350L
        assertThat(saved.getEstimateRecruiterCommission()).isEqualTo(250L);
        assertThat(saved.getEstimateChannelCommission()).isEqualTo(350L);
        assertThat(saved.getRecruiterCommissionRate()).isEqualTo(new BigDecimal("0.25"));
        assertThat(saved.getChannelCommissionRate()).isEqualTo(new BigDecimal("0.35"));
    }

    @Test
    void upsertFromOrder_shouldHandleAttributionCorrectlyWhenUserIdsMissing() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-NO-ATTRIBUTION");
        order.setEstimateServiceFee(1000L);
        order.setOrderStatus(1);
        // Missing channelUserId and colonelUserId/userId
        order.setChannelUserId(null);
        order.setColonelUserId(null);
        order.setUserId(null);

        when(performanceRecordMapper.findByOrderId("ORD-NO-ATTRIBUTION")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord saved = service.upsertFromOrder(order);

        assertThat(saved).isNotNull();
        assertThat(saved.getDefaultChannelUserId()).isNull();
        assertThat(saved.getDefaultRecruiterUserId()).isNull();
        assertThat(saved.getChannelAttribution()).isEqualTo("unattributed");
        assertThat(saved.getRecruiterAttribution()).isEqualTo("unattributed");
    }

    @Test
    void upsertFromOrder_shouldCorrectlyMapServiceFeeExpensesOnBothTracks() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-EXPENSES");
        order.setEstimateServiceFee(1000L);
        order.setEstimateTechServiceFee(100L);
        order.setEstimateServiceFeeExpense(150L);
        
        order.setEffectiveServiceFee(800L);
        order.setEffectiveTechServiceFee(80L);
        order.setEffectiveServiceFeeExpense(120L);
        order.setOrderStatus(1);

        when(performanceRecordMapper.findByOrderId("ORD-EXPENSES")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord saved = service.upsertFromOrder(order);

        assertThat(saved).isNotNull();
        // Check mapping
        assertThat(saved.getEstimateServiceFeeExpense()).isEqualTo(150L);
        assertThat(saved.getEffectiveServiceFeeExpense()).isEqualTo(120L);
        
        // Net profit calculation
        // Estimate Net = 1000 - 100 - 150 = 750L
        // Effective Net = 800 - 120 = 680L (as effective tech fee is not deducted again)
        assertThat(saved.getEstimateServiceProfit()).isEqualTo(750L);
        assertThat(saved.getEffectiveServiceProfit()).isEqualTo(680L);
    }
}
