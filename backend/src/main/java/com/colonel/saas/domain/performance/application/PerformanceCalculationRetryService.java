package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.order.event.OrderRefundFactSyncedEvent;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceCalculationExecution;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/** 按执行台账重试失败的业绩计算，并重新发布业绩计算完成事件。 */
@Service
public class PerformanceCalculationRetryService {

    private final PerformanceCalculationExecutionService executionService;
    private final OrderReadFacade orderReadFacade;
    private final PerformanceCalculationApplicationService calculationService;
    private final ApplicationEventPublisher eventPublisher;
    private final PerformanceRefundAdjustmentService refundAdjustmentService;

    public PerformanceCalculationRetryService(
            PerformanceCalculationExecutionService executionService,
            OrderReadFacade orderReadFacade,
            PerformanceCalculationApplicationService calculationService,
            ApplicationEventPublisher eventPublisher) {
        this(executionService, orderReadFacade, calculationService, eventPublisher, null);
    }

    @Autowired
    public PerformanceCalculationRetryService(
            PerformanceCalculationExecutionService executionService,
            OrderReadFacade orderReadFacade,
            PerformanceCalculationApplicationService calculationService,
            ApplicationEventPublisher eventPublisher,
            PerformanceRefundAdjustmentService refundAdjustmentService) {
        this.executionService = executionService;
        this.orderReadFacade = orderReadFacade;
        this.calculationService = calculationService;
        this.eventPublisher = eventPublisher;
        this.refundAdjustmentService = refundAdjustmentService;
    }

    public RetryResult retryDue(int limit) {
        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        for (PerformanceCalculationExecution execution : executionService.findRetryDue(limit)) {
            attempted++;
            if (!executionService.start(
                    execution.getEventKey(), execution.getEventType(), execution.getOrderId(), versionOf(execution))) {
                continue;
            }
            try {
                ColonelsettlementOrder order = orderReadFacade.findByOrderId(execution.getOrderId());
                if (order == null) {
                    throw new IllegalStateException("Performance retry order not found: " + execution.getOrderId());
                }
                PerformanceRecord record = calculationService.upsertFromOrder(order);
                if (record != null) {
                    recoverRefundAdjustment(execution, order, record);
                    eventPublisher.publishEvent(toCalculatedEvent(record));
                }
                executionService.markSucceeded(execution.getEventKey());
                succeeded++;
            } catch (RuntimeException error) {
                executionService.markFailed(execution.getEventKey(), error);
                failed++;
            }
        }
        return new RetryResult(attempted, succeeded, failed);
    }

    private static int versionOf(PerformanceCalculationExecution execution) {
        return execution.getOrderVersion() == null ? 0 : execution.getOrderVersion();
    }

    private void recoverRefundAdjustment(
            PerformanceCalculationExecution execution,
            ColonelsettlementOrder order,
            PerformanceRecord record) {
        if (!"OrderRefundFactSynced".equals(execution.getEventType()) || refundAdjustmentService == null) {
            return;
        }
        Map<String, Object> payload = execution.getEventPayload() == null ? Map.of() : execution.getEventPayload();
        refundAdjustmentService.recordRefund(record, new OrderRefundFactSyncedEvent(
                execution.getOrderId(),
                order.getId(),
                asString(payload.get("refundId")),
                asLong(payload.get("refundAmount")),
                asInteger(payload.get("previousStatus")),
                asInteger(payload.get("status")),
                asString(payload.get("flowPoint")),
                asMap(payload.get("extraData")),
                asTime(payload.get("occurredAt"))));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer asInteger(Object value) {
        Long parsed = asLong(value);
        return parsed == null ? null : parsed.intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static LocalDateTime asTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static PerformanceCalculatedEvent toCalculatedEvent(PerformanceRecord record) {
        return new PerformanceCalculatedEvent(
                record.getOrderId(),
                record.getFinalChannelUserId(),
                record.getFinalRecruiterUserId(),
                nvl(record.getEstimateRecruiterCommission()),
                nvl(record.getEffectiveRecruiterCommission()),
                nvl(record.getEstimateChannelCommission()),
                nvl(record.getEffectiveChannelCommission()),
                nvl(record.getEstimateGrossProfit()),
                nvl(record.getEffectiveGrossProfit()),
                Boolean.TRUE.equals(record.getReversed()) ? "REVERSAL" : "NORMAL",
                Boolean.TRUE.equals(record.getReversed()));
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }

    public record RetryResult(int attempted, int succeeded, int failed) {
    }
}
