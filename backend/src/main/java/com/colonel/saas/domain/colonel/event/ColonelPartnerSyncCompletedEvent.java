package com.colonel.saas.domain.colonel.event;

import com.colonel.saas.entity.ColonelPartner;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 团长主数据同步完成事件（DDD-COLONEL-002 Wave 1.3 补全）。
 *
 * <p>由 {@code ColonelPartnerSyncApplicationService} 发布，标识一次同步周期完成。
 * 事件消费者可基于此事件触发下游缓存刷新、统计更新等。</p>
 */
public record ColonelPartnerSyncCompletedEvent(
        /** 事件 ID */
        UUID eventId,
        /** 同步周期内 upsert 的团长数量 */
        int upsertedCount,
        /** 事件发生时间 */
        LocalDateTime occurredAt,
        /** 触发同步的操作人（系统任务时为 null） */
        UUID operatorId) {

    /**
     * 工厂方法：构造同步完成事件。
     */
    public static ColonelPartnerSyncCompletedEvent of(int upsertedCount) {
        return new ColonelPartnerSyncCompletedEvent(
                UUID.randomUUID(),
                upsertedCount,
                LocalDateTime.now(),
                null);
    }

    /**
     * 工厂方法：构造带操作人的同步完成事件。
     */
    public static ColonelPartnerSyncCompletedEvent of(int upsertedCount, UUID operatorId) {
        return new ColonelPartnerSyncCompletedEvent(
                UUID.randomUUID(),
                upsertedCount,
                LocalDateTime.now(),
                operatorId);
    }

    /**
     * 工厂方法：从已 upsert 的团长实体列表构造事件。
     */
    public static ColonelPartnerSyncCompletedEvent fromPartners(java.util.List<ColonelPartner> partners) {
        return ColonelPartnerSyncCompletedEvent.of(partners == null ? 0 : partners.size());
    }
}
