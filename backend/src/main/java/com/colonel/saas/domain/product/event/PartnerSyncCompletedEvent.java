package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;

/**
 * 团长合作方主数据同步完成事件。
 *
 * <p>当单个合作方（达人/商家）的主数据从抖音同步完成时触发。
 * 该事件通过 {@link ProductDomainEventPublisher#publishPartnerSyncCompleted} 发布，
 * 同时写入 Outbox 表和 Spring 本地事件。</p>
 *
 * <p>典型触发场景：
 * <ul>
 *   <li>定时任务同步合作方主数据</li>
 *   <li>首次关联合作方时拉取最新数据</li>
 *   <li>合作方信息变更后的增量更新</li>
 * </ul>
 */
public record PartnerSyncCompletedEvent(
        /** 事件唯一标识（UUID 字符串形式）。 */
        String eventId,
        /** 合作方 ID（抖音侧标识）。 */
        String partnerId,
        /** 合作方名称。 */
        String partnerName,
        /** 合作方类型（如达人、商家等）。 */
        String partnerType,
        /** 数据来源标识。 */
        String source,
        /** 同步状态：{@code SUCCESS}、{@code FAILED} 等。 */
        String syncStatus,
        /** 是否为新建记录（首次同步）。 */
        boolean created,
        /** 是否为更新记录（已有记录的增量更新）。 */
        boolean updated,
        /** 同步完成时间。 */
        LocalDateTime occurredAt,
        /** 链路追踪 ID（从 MDC 提取，可为 null）。 */
        String traceId) {
}
