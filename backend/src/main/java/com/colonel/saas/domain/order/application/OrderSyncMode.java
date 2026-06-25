package com.colonel.saas.domain.order.application;

/**
 * 订单同步触发模式（DDD-ORDER-001）。
 */
public enum OrderSyncMode {
    /** 定时任务触发的增量/补偿同步 */
    SCHEDULED,
    /** 管理员手动触发（默认增量窗口） */
    MANUAL,
    /** 仅预览：不委派落库同步（旧 OrderSyncService 无 dry-run 时由应用层跳过） */
    DRY_RUN,
    /** 按显式时间范围补拉历史订单 */
    HISTORICAL
}
