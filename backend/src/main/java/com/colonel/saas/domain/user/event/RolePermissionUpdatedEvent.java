package com.colonel.saas.domain.user.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 角色权限已更新（当前主路径为角色菜单关联覆盖保存）。
 */
public record RolePermissionUpdatedEvent(
        UUID eventId,
        UUID roleId,
        String roleCode,
        String oldPermissionHash,
        String newPermissionHash,
        UUID operatorId,
        LocalDateTime occurredAt,
        String traceId) {
}
