package com.colonel.saas.domain.performance.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.order.event.OrderRefundFactSyncedEvent;
import com.colonel.saas.entity.PerformanceAdjustmentLedger;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.mapper.PerformanceAdjustmentLedgerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceRefundAdjustmentServiceTest {

    @Mock private PerformanceAdjustmentLedgerMapper ledgerMapper;

    @Test
    void recordRefundShouldCreateIdempotentProportionalAdjustmentForPartialRefund() {
        PerformanceRefundAdjustmentService service = new PerformanceRefundAdjustmentService(ledgerMapper);
        when(ledgerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        PerformanceRecord record = new PerformanceRecord();
        record.setOrderId("ORD-PARTIAL-REFUND");
        record.setPayAmount(1_000L);
        record.setEffectiveServiceFee(400L);
        record.setEffectiveTechServiceFee(40L);
        record.setEffectiveServiceFeeExpense(80L);
        record.setTalentCommission(60L);
        record.setEffectiveServiceProfit(200L);
        record.setEffectiveRecruiterCommission(40L);
        record.setEffectiveChannelCommission(20L);
        record.setEffectiveGrossProfit(140L);
        OrderRefundFactSyncedEvent event = new OrderRefundFactSyncedEvent(
                "ORD-PARTIAL-REFUND", UUID.randomUUID(), "REFUND-1", 250L,
                3, 3, "REFUND", Map.of(), LocalDateTime.of(2026, 7, 16, 12, 0));

        assertThat(service.recordRefund(record, event).getDeltaEffectiveServiceProfit()).isEqualTo(-50L);

        ArgumentCaptor<PerformanceAdjustmentLedger> captor = ArgumentCaptor.forClass(PerformanceAdjustmentLedger.class);
        verify(ledgerMapper).insert(captor.capture());
        assertThat(captor.getValue().getEventKey()).isEqualTo("OrderRefundFactSynced:ORD-PARTIAL-REFUND:REFUND-1");
        assertThat(captor.getValue().getDeltaEffectiveServiceFee()).isEqualTo(-100L);
        assertThat(captor.getValue().getDeltaEffectiveTechServiceFee()).isEqualTo(-10L);
        assertThat(captor.getValue().getDeltaEffectiveServiceFeeExpense()).isEqualTo(-20L);
        assertThat(captor.getValue().getDeltaTalentCommission()).isEqualTo(-15L);
        assertThat(captor.getValue().getDeltaEffectiveRecruiterCommission()).isEqualTo(-10L);
    }
}
