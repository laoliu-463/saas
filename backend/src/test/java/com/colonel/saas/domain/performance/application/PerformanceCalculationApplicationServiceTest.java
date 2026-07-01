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
}