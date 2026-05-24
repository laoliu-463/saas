package com.colonel.saas.domain.user.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户组别变更（技术实现：sys_user.dept_id 指向 sys_dept 组织单元）。
 */
public record UserGroupChangedEvent(
        UUID eventId,
        UUID userId,
        UUID oldGroupId,
        UUID newGroupId,
        UUID oldDeptId,
        UUID newDeptId,
        UUID operatorId,
        LocalDateTime occurredAt,
        String traceId) {
}
