package com.colonel.saas.service;

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
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceCalculationEffectiveTrackTest {

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
    void upsertFromOrder_shouldUseEffectiveTrackWithoutCallingDouyinOrChangingFormula() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORDER-1603-PERF");
        order.setActivityId("ACT-1");
        order.setOrderAmount(10000L);
        order.setSettleAmount(8000L);
        order.setEstimateServiceFee(1000L);
        order.setEstimateTechServiceFee(100L);
        order.setEffectiveServiceFee(720L);
        order.setEffectiveTechServiceFee(80L);
        order.setOrderStatus(1);

        when(performanceRecordMapper.findByOrderId("ORDER-1603-PERF")).thenReturn(null);
        when(performanceRecordMapper.upsert(any())).thenReturn(1);

        service.upsertFromOrder(order);

        ArgumentCaptor<PerformanceRecord> captor = ArgumentCaptor.forClass(PerformanceRecord.class);
        verify(performanceRecordMapper).upsert(captor.capture());
        PerformanceRecord record = captor.getValue();
        assertThat(record.getSettleAmount()).isEqualTo(8000L);
        assertThat(record.getEffectiveServiceFee()).isEqualTo(720L);
        assertThat(record.getEffectiveTechServiceFee()).isEqualTo(80L);
        assertThat(record.getEffectiveServiceProfit()).isEqualTo(720L);
        assertThat(record.getEffectiveRecruiterCommission()).isEqualTo(72L);
        assertThat(record.getEffectiveChannelCommission()).isEqualTo(144L);
        assertThat(record.getEffectiveGrossProfit()).isEqualTo(504L);
    }
}
