package com.colonel.saas.domain.event;

/**
 * 领域事件在 Outbox 表中的生命周期状态枚举。
 *
 * <p>事件从 PENDING 起步，经由事件派发器（OutboxDispatcher）取出并投递。
 * 投递成功则进入 PUBLISHED，失败则进入 FAILED 并触发指数退避重试。
 * 若重试次数超过 {@code max_retry} 阈值，则转入 DEAD 状态等待人工介入。</p>
 *
 * <p>状态流转图：
 * <pre>
 *   PENDING ──▶ PROCESSING ──▶ PUBLISHED  （投递成功）
 *                   │
 *                   ▼
 *                FAILED ──(重试)──▶ PENDING  （退避后重试）
 *                   │
 *                   ▼
 *                DEAD  （超过最大重试次数，需人工干预）
 * </pre>
 * </p>
 */
public enum DomainEventStatus {

    /** 待处理：事件已写入 Outbox 表，等待派发器捞取。 */
    PENDING,

    /** 处理中：派发器已锁定该事件，正在执行投递逻辑（用于并发控制）。 */
    PROCESSING,

    /** 已发布：事件已成功投递至下游消费者或消息中间件。 */
    PUBLISHED,

    /** 投递失败：本次投递失败，将根据指数退避策略安排下次重试。 */
    FAILED,

    /** 已死亡：重试次数耗尽，事件不再自动重试，需运维人员手动排查并重放。 */
    DEAD
}
