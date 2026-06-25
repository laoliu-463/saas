package com.colonel.saas.domain.order.application;

/**
 * 订单同步执行上下文：标识定时子任务与触发来源（DDD-ORDER-001）。
 */
public record OrderSyncExecutionContext(
        String scheduledTask,
        String triggerSource
) {
    public static final String TASK_INCREMENTAL = "INCREMENTAL";
    public static final String TASK_PAY_RECENT = "PAY_RECENT";
    public static final String TASK_INSTITUTE_HOT = "INSTITUTE_HOT_RECENT";
    public static final String TASK_INSTITUTE_RECENT = "INSTITUTE_RECENT";
    public static final String TASK_SETTLE = "SETTLE";
    public static final String TASK_INSTITUTE_BACKFILL = "INSTITUTE_FULL_BACKFILL";

    public static OrderSyncExecutionContext scheduled(String scheduledTask) {
        return new OrderSyncExecutionContext(scheduledTask, "OrderSyncJob");
    }

    public static OrderSyncExecutionContext manual(String triggerSource) {
        return new OrderSyncExecutionContext(null, triggerSource);
    }
}
