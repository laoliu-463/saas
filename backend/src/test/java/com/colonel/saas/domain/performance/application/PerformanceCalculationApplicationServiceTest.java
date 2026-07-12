package com.colonel.saas.domain.performance.application;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.mapper.PerformanceRecordMapper;
import com.colonel.saas.service.CommissionRuleService;
import com.colonel.saas.service.CommissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * PerformanceCalculationApplicationService 直接行为验证（DDD-PERFORMANCE Slice 7）。
 *
 * <p>核心目标：验证 Application 层独立持有完整的 upsertFromOrder 业务逻辑
 * （buildRecord 双轨计算 + OrderCommissionPolicy 已取消订单置零），
 * 不依赖 Service 中转。Service 层委派是 thin shell，
 * 真实业务逻辑必须由本测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class PerformanceCalculationApplicationServiceTest {

    @Mock
    private PerformanceRecordMapper performanceRecordMapper;
    @Mock
    private ConfigDomainFacade configDomainFacade;
    @Mock
    private CommissionRuleService commissionRuleService;

    private PerformanceCalculationApplicationService applicationService;

    @BeforeEach
    void setUp() {
        // DDD-PERFORMANCE Slice 7: 构造完整 CommissionService + Application 链。
        // 测试通过 mock 共享 configDomainFacade / commissionRuleService 验证 Application 行为。
        CommissionService commissionService = new CommissionService(
                configDomainFacade, commissionRuleService, null);
        lenient().when(commissionRuleService.resolveRule(any(), any(), any())).thenReturn(null);
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
        applicationService = new PerformanceCalculationApplicationService(
                performanceRecordMapper, commissionService);
    }

    @Test
    void upsertFromOrder_shouldReturnNullForNullOrder() {
        assertThat(applicationService.upsertFromOrder(null)).isNull();
    }

    @Test
    void upsertFromOrder_shouldReturnNullForBlankOrderId() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("   ");
        assertThat(applicationService.upsertFromOrder(order)).isNull();
    }

    @Test
    void upsertFromOrder_shouldCalculateEstimateAndEffectiveTracks() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(java.util.UUID.randomUUID());
        order.setOrderId("ORD-APP-100");
        order.setActivityId("ACT-1");
        order.setOrderAmount(10000L);
        order.setSettleAmount(10000L);
        order.setEstimateServiceFee(1000L);
        order.setEffectiveServiceFee(900L);
        order.setEstimateTechServiceFee(100L);
        order.setEffectiveTechServiceFee(90L);
        order.setEstimateServiceFeeExpense(200L);
        order.setEffectiveServiceFeeExpense(180L);
        order.setSettleSecondColonelCommission(50L);
        order.setChannelUserId(java.util.UUID.randomUUID());
        order.setColonelUserId(java.util.UUID.randomUUID());
        order.setOrderStatus(1);

        when(performanceRecordMapper.findByOrderId("ORD-APP-100")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord result = applicationService.upsertFromOrder(order);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("ORD-APP-100");
        // estimate 轨: serviceFeeNet = 1000 - 100 - 200 = 700 (estimate track 不用 settleSecondColonelCommission)
        assertThat(result.getEstimateServiceProfit()).isEqualTo(700L);
        // effective 轨: serviceFeeNet = 900 - 180 = 720 (effective track 不扣 effectiveTechServiceFee)
        assertThat(result.getEffectiveServiceProfit()).isEqualTo(720L);
    }

    @Test
    void upsertFromOrder_shouldPreserveTraceableAttributionInputsOnPerformanceRecord() {
        UUID channelUserId = UUID.randomUUID();
        UUID recruiterUserId = UUID.randomUUID();
        UUID fallbackUserId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-TRACE-1");
        order.setChannelUserId(channelUserId);
        order.setColonelUserId(recruiterUserId);
        order.setUserId(fallbackUserId);
        order.setTalentId(talentId);
        order.setShopId(90000001L);
        order.setProductId("PROD-TRACE-1");
        order.setActivityId("ACT-TRACE-1");
        order.setOrderStatus(1);

        when(performanceRecordMapper.findByOrderId("ORD-TRACE-1")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord result = applicationService.upsertFromOrder(order);

        assertThat(result).isNotNull();
        assertThat(result.getDefaultChannelUserId()).isEqualTo(channelUserId);
        assertThat(result.getDefaultRecruiterUserId()).isEqualTo(recruiterUserId);
        assertThat(result.getFinalChannelUserId()).isEqualTo(channelUserId);
        assertThat(result.getFinalRecruiterUserId()).isEqualTo(recruiterUserId);
        assertThat(result.getChannelAttribution()).isEqualTo("pick_source");
        assertThat(result.getRecruiterAttribution()).isEqualTo("activity_owner");
        assertThat(result.getTalentId()).isEqualTo(talentId);
        assertThat(result.getPartnerId()).isEqualTo(90000001L);
        assertThat(result.getProductId()).isEqualTo("PROD-TRACE-1");
        assertThat(result.getActivityId()).isEqualTo("ACT-TRACE-1");
    }

    @Test
    void upsertFromOrder_existingRecordShouldReuseIdAndAdvanceVersionForDuplicateConsumption() {
        UUID existingId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 8, 10, 0);
        PerformanceRecord existing = new PerformanceRecord();
        existing.setId(existingId);
        existing.setCalculationVersion(3);
        existing.setCreatedAt(createdAt);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-DUPLICATE-CONSUMPTION");
        order.setOrderStatus(1);
        order.setEstimateServiceFee(1000L);
        order.setEstimateTechServiceFee(100L);
        order.setEstimateServiceFeeExpense(200L);

        when(performanceRecordMapper.findByOrderId("ORD-DUPLICATE-CONSUMPTION")).thenReturn(existing);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord result = applicationService.upsertFromOrder(order);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(existingId);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        assertThat(result.getCalculationVersion()).isEqualTo(4);
    }

    @Test
    void upsertFromOrder_shouldZeroCommissionsForCancelledOrder() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(java.util.UUID.randomUUID());
        order.setOrderId("ORD-CANCELLED");
        order.setActivityId("ACT-1");
        order.setOrderAmount(10000L);
        order.setEstimateServiceFee(1000L);
        order.setEstimateTechServiceFee(100L);
        // 订单状态 4 = CANCELLED (按 OrderCommissionPolicy.countsTowardPerformance 规则)
        order.setOrderStatus(4);

        when(performanceRecordMapper.findByOrderId("ORD-CANCELLED")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord result = applicationService.upsertFromOrder(order);

        assertThat(result).isNotNull();
        // 已取消订单所有提成和收益字段置零
        assertThat(result.getValid()).isFalse();
        assertThat(result.getReversed()).isTrue();
        assertThat(result.getEstimateServiceProfit()).isZero();
        assertThat(result.getEffectiveServiceProfit()).isZero();
        assertThat(result.getEstimateRecruiterCommission()).isZero();
        assertThat(result.getEffectiveRecruiterCommission()).isZero();
        assertThat(result.getEstimateChannelCommission()).isZero();
        assertThat(result.getEffectiveChannelCommission()).isZero();
        assertThat(result.getEstimateGrossProfit()).isZero();
        assertThat(result.getEffectiveGrossProfit()).isZero();
    }

    @Test
    void upsertFromOrder_shouldReverseRefundedExistingRecordAndAdvanceVersion() {
        UUID recordId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 30);
        PerformanceRecord existing = new PerformanceRecord();
        existing.setId(recordId);
        existing.setCalculationVersion(7);
        existing.setCreatedAt(createdAt);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-REFUNDED-EXISTING");
        order.setOrderStatus(5);
        order.setOrderAmount(10000L);
        order.setSettleAmount(9000L);
        order.setEstimateServiceFee(1000L);
        order.setEffectiveServiceFee(900L);
        order.setEstimateTechServiceFee(100L);
        order.setEffectiveTechServiceFee(90L);
        order.setEstimateServiceFeeExpense(200L);
        order.setEffectiveServiceFeeExpense(180L);

        when(performanceRecordMapper.findByOrderId("ORD-REFUNDED-EXISTING")).thenReturn(existing);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        PerformanceRecord result = applicationService.upsertFromOrder(order);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(recordId);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        assertThat(result.getCalculationVersion()).isEqualTo(8);
        assertThat(result.getOrderStatus()).isEqualTo(5);
        assertThat(result.getValid()).isFalse();
        assertThat(result.getReversed()).isTrue();
        assertThat(result.getEstimateServiceProfit()).isZero();
        assertThat(result.getEffectiveServiceProfit()).isZero();
        assertThat(result.getEstimateServiceFeeExpense()).isZero();
        assertThat(result.getEffectiveServiceFeeExpense()).isZero();
        assertThat(result.getEstimateRecruiterCommission()).isZero();
        assertThat(result.getEffectiveRecruiterCommission()).isZero();
        assertThat(result.getEstimateChannelCommission()).isZero();
        assertThat(result.getEffectiveChannelCommission()).isZero();
        assertThat(result.getEstimateGrossProfit()).isZero();
        assertThat(result.getEffectiveGrossProfit()).isZero();
    }
}
