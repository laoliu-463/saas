package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.mapper.PerformanceRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.time.LocalDateTime;

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
    private JdbcTemplate jdbcTemplate;
    @Mock
    private CommissionRuleService commissionRuleService;

    private PerformanceCalculationService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceCalculationService(
                performanceRecordMapper,
                new CommissionService(jdbcTemplate, commissionRuleService, null));
        lenient().when(commissionRuleService.resolveRatio(any(), any(), any())).thenReturn(null);
        lenient().when(jdbcTemplate.query(
                        anyString(),
                        org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(),
                        anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(2);
                    if (key != null && key.contains("business")) {
                        return "0.10";
                    }
                    return "0.20";
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
        assertThat(record.getEffectiveServiceProfit()).isEqualTo(810L);
        assertThat(record.getEstimateRecruiterCommission()).isEqualTo(90L);
        assertThat(record.getEffectiveRecruiterCommission()).isEqualTo(81L);
        assertThat(record.getEstimateChannelCommission()).isEqualTo(180L);
        assertThat(record.getEffectiveChannelCommission()).isEqualTo(162L);
        assertThat(record.getEstimateGrossProfit()).isEqualTo(630L);
        assertThat(record.getEffectiveGrossProfit()).isEqualTo(567L);
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
}
