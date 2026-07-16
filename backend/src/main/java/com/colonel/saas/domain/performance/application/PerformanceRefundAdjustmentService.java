package com.colonel.saas.domain.performance.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.order.event.OrderRefundFactSyncedEvent;
import com.colonel.saas.entity.PerformanceAdjustmentLedger;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.mapper.PerformanceAdjustmentLedgerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 将退款事实转换为可审计、可幂等的业绩调整流水。 */
@Service
public class PerformanceRefundAdjustmentService {

    private final PerformanceAdjustmentLedgerMapper ledgerMapper;

    public PerformanceRefundAdjustmentService(PerformanceAdjustmentLedgerMapper ledgerMapper) {
        this.ledgerMapper = ledgerMapper;
    }

    /**
     * 以退款事件键去重。部分退款按退款额占订单实付额比例反向调整业绩金额；
     * 已整单冲正的订单仍写入零额审计流水，不再与已失效的基础业绩重复扣减。
     */
    @Transactional(rollbackFor = Exception.class)
    public PerformanceAdjustmentLedger recordRefund(
            PerformanceRecord record, OrderRefundFactSyncedEvent event) {
        if (record == null || event == null || event.orderId() == null) {
            throw new IllegalArgumentException("Performance refund adjustment requires record and refund event");
        }
        String eventKey = eventKey(event);
        PerformanceAdjustmentLedger existing = ledgerMapper.selectOne(new LambdaQueryWrapper<PerformanceAdjustmentLedger>()
                .eq(PerformanceAdjustmentLedger::getEventKey, eventKey)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        long payAmount = positive(record.getPayAmount());
        long requestedRefund = positive(event.refundAmount());
        long refundAmount = Math.min(requestedRefund, payAmount);
        boolean wholeOrderReversed = Boolean.TRUE.equals(record.getReversed()) || Boolean.FALSE.equals(record.getValid());
        long numerator = wholeOrderReversed ? 0L : refundAmount;
        PerformanceAdjustmentLedger ledger = new PerformanceAdjustmentLedger();
        ledger.setId(UUID.randomUUID());
        ledger.setEventKey(eventKey);
        ledger.setOrderId(record.getOrderId());
        ledger.setRefundId(event.refundId());
        ledger.setAdjustmentType(wholeOrderReversed ? "REVERSAL" : "REFUND");
        ledger.setRefundAmount(refundAmount);
        ledger.setDeltaPayAmount(-numerator);
        ledger.setDeltaSettleAmount(-proportion(record.getSettleAmount(), numerator, payAmount));
        ledger.setDeltaEstimateServiceFee(-proportion(record.getEstimateServiceFee(), numerator, payAmount));
        ledger.setDeltaEffectiveServiceFee(-proportion(record.getEffectiveServiceFee(), numerator, payAmount));
        ledger.setDeltaEstimateTechServiceFee(-proportion(record.getEstimateTechServiceFee(), numerator, payAmount));
        ledger.setDeltaEffectiveTechServiceFee(-proportion(record.getEffectiveTechServiceFee(), numerator, payAmount));
        ledger.setDeltaEstimateServiceFeeExpense(-proportion(record.getEstimateServiceFeeExpense(), numerator, payAmount));
        ledger.setDeltaEffectiveServiceFeeExpense(-proportion(record.getEffectiveServiceFeeExpense(), numerator, payAmount));
        ledger.setDeltaTalentCommission(-proportion(record.getTalentCommission(), numerator, payAmount));
        ledger.setDeltaEstimateServiceProfit(-proportion(record.getEstimateServiceProfit(), numerator, payAmount));
        ledger.setDeltaEffectiveServiceProfit(-proportion(record.getEffectiveServiceProfit(), numerator, payAmount));
        ledger.setDeltaEstimateRecruiterCommission(-proportion(record.getEstimateRecruiterCommission(), numerator, payAmount));
        ledger.setDeltaEffectiveRecruiterCommission(-proportion(record.getEffectiveRecruiterCommission(), numerator, payAmount));
        ledger.setDeltaEstimateChannelCommission(-proportion(record.getEstimateChannelCommission(), numerator, payAmount));
        ledger.setDeltaEffectiveChannelCommission(-proportion(record.getEffectiveChannelCommission(), numerator, payAmount));
        ledger.setDeltaEstimateGrossProfit(-proportion(record.getEstimateGrossProfit(), numerator, payAmount));
        ledger.setDeltaEffectiveGrossProfit(-proportion(record.getEffectiveGrossProfit(), numerator, payAmount));
        ledger.setOccurredAt(event.occurredAt() == null ? LocalDateTime.now() : event.occurredAt());
        ledger.setInputSnapshot(snapshot(record, event, wholeOrderReversed));
        ledger.setCreatedAt(LocalDateTime.now());
        ledgerMapper.insert(ledger);
        return ledger;
    }

    public static String eventKey(OrderRefundFactSyncedEvent event) {
        String refundIdentity = event.refundId() == null || event.refundId().isBlank()
                ? String.valueOf(event.occurredAt())
                : event.refundId();
        return "OrderRefundFactSynced:" + event.orderId() + ":" + refundIdentity;
    }

    private static long proportion(Long value, long numerator, long denominator) {
        if (value == null || value == 0L || numerator == 0L || denominator <= 0L) {
            return 0L;
        }
        return BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(numerator))
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private static long positive(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    private static Map<String, Object> snapshot(
            PerformanceRecord record, OrderRefundFactSyncedEvent event, boolean wholeOrderReversed) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recordCalculationVersion", record.getCalculationVersion());
        snapshot.put("refundPreviousStatus", event.previousStatus());
        snapshot.put("refundStatus", event.status());
        snapshot.put("flowPoint", event.flowPoint());
        snapshot.put("wholeOrderReversed", wholeOrderReversed);
        return snapshot;
    }
}
