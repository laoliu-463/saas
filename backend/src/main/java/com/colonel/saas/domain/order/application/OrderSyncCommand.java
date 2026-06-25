package com.colonel.saas.domain.order.application;

import java.util.UUID;

/**
 * 订单同步应用层命令（DDD-ORDER-001）。
 */
public record OrderSyncCommand(
        OrderSyncMode mode,
        OrderSyncTimeType timeType,
        Long startTime,
        Long endTime,
        Integer maxPages,
        Integer maxOrders,
        UUID operatorId,
        boolean dryRun
) {
    public static OrderSyncCommand scheduledIncremental() {
        return new OrderSyncCommand(
                OrderSyncMode.SCHEDULED,
                OrderSyncTimeType.UPDATE,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    public static OrderSyncCommand scheduledPayRecent() {
        return new OrderSyncCommand(
                OrderSyncMode.SCHEDULED,
                OrderSyncTimeType.UPDATE,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    public static OrderSyncCommand scheduledSettle() {
        return new OrderSyncCommand(
                OrderSyncMode.SCHEDULED,
                OrderSyncTimeType.SETTLE,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    public static OrderSyncCommand historical(long startTime, long endTime, UUID operatorId) {
        return new OrderSyncCommand(
                OrderSyncMode.HISTORICAL,
                OrderSyncTimeType.UPDATE,
                startTime,
                endTime,
                null,
                null,
                operatorId,
                false);
    }

    public static OrderSyncCommand dryRunPreview(OrderSyncTimeType timeType, UUID operatorId) {
        return new OrderSyncCommand(
                OrderSyncMode.DRY_RUN,
                timeType,
                null,
                null,
                null,
                null,
                operatorId,
                true);
    }
}
