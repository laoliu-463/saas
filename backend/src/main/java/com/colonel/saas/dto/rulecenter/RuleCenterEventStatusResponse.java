package com.colonel.saas.dto.rulecenter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 规则中心事件状态响应 DTO。
 * <p>
 * 返回规则变更事件的发布和消费状态，包括事件基础信息、重试次数、错误信息
 * 以及各消费者的消费状态明细。
 * 关联业务领域：规则中心（RuleCenter）。
 * </p>
 */
public record RuleCenterEventStatusResponse(
        /** 事件 ID */
        UUID eventId,
        /** 事件类型（如 config_updated） */
        String eventType,
        /** 事件状态（如 pending、published、consumed、failed） */
        String status,
        /** 重试次数 */
        Integer retryCount,
        /** 错误信息（失败时有值） */
        String errorMessage,
        /** 事件发生时间 */
        LocalDateTime occurredAt,
        /** 事件发布时间 */
        LocalDateTime publishedAt,
        /** 各消费者的消费状态列表 */
        List<ConsumerStatusView> consumers) {

    /**
     * 消费者状态视图内部类。
     */
    public record ConsumerStatusView(
            /** 消费者名称（消费域标识） */
            String consumerName,
            /** 消费状态（如 consumed、pending、failed） */
            String status,
            /** 消费错误信息（失败时有值） */
            String errorMessage,
            /** 消费完成时间 */
            LocalDateTime consumedAt) {
    }
}
