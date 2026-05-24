package com.colonel.saas.domain.user.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户已创建（新员工入职）。
 */
public record UserCreatedEvent(
        UUID eventId,
        UUID userId,
        String username,
        String realName,
        UUID roleId,
        String roleCode,
        UUID groupId,
        UUID deptId,
        String status,
        UUID operatorId,
        LocalDateTime occurredAt,
        String traceId) {
}
