package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 活动商品同步完成事件。
 *
 * <p>当抖音活动的商品数据（选品库/精选联盟）同步完成时触发。
 * 该事件通过 {@link ProductDomainEventPublisher#publishActivitySyncCompleted} 发布，
 * 同时写入 Outbox 表和 Spring 本地事件。</p>
 *
 * <p>典型触发场景：
 * <ul>
 *   <li>定时任务全量同步活动商品</li>
 *   <li>手动触发增量同步</li>
 *   <li>活动新增商品后自动同步</li>
 * </ul>
 */
public record ActivitySyncCompletedEvent(
        /** 事件唯一标识（UUID 字符串形式）。 */
        String eventId,
        /** 抖音活动 ID。 */
        String activityId,
        /** 活动名称（可为 null）。 */
        String activityName,
        /** 同步类型：{@code FULL}（全量）或 {@code INCREMENTAL}（增量）。 */
        String syncType,
        /** 本次同步新创建的商品数量。 */
        int createdCount,
        /** 本次同步更新的商品数量。 */
        int updatedCount,
        /** 本次同步跳过的商品数量（数据无变化）。 */
        int skippedCount,
        /** 同步最终状态：{@code SUCCESS}、{@code PARTIAL}、{@code FAILED}。 */
        String syncStatus,
        /** 触发同步的操作人 ID（系统自动同步时为 null）。 */
        UUID operatorId,
        /** 同步完成时间。 */
        LocalDateTime occurredAt,
        /** 链路追踪 ID（从 MDC 提取，可为 null）。 */
        String traceId) {
}
