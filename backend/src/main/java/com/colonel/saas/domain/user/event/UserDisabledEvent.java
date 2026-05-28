package com.colonel.saas.domain.user.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户已禁用事件（离职/停用）。
 *
 * <p>当管理员将用户状态从启用变更为禁用时触发。典型场景包括员工离职、账号停用等。
 * 该事件用于通知相关模块清理用户关联数据（如会话失效、权限缓存清除等）。</p>
 */
public record UserDisabledEvent(
        /** 事件唯一标识。 */
        UUID eventId,
        /** 被禁用的用户 ID。 */
        UUID userId,
        /** 变更前的用户状态（如 ACTIVE）。 */
        String oldStatus,
        /** 变更后的用户状态（如 DISABLED）。 */
        String newStatus,
        /** 操作人 ID（执行禁用操作的管理员）。 */
        UUID operatorId,
        /** 事件发生时间。 */
        LocalDateTime occurredAt,
        /** 链路追踪 ID（从 MDC 提取，可为 null）。 */
        String traceId) {
}
