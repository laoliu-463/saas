package com.colonel.saas.domain.user.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 角色权限已更新事件。
 *
 * <p>当角色的菜单权限关联被覆盖保存时触发。当前主路径为管理员在角色管理页面
 * 重新分配菜单权限后，系统整体替换该角色的菜单关联。</p>
 *
 * <p>使用 SHA-256 哈希（{@link com.colonel.saas.domain.user.PermissionEventHasher}）
 * 代替完整权限 JSON，减少事件载荷体积，同时保证变更可检测。</p>
 */
public record RolePermissionUpdatedEvent(
        /** 事件唯一标识。 */
        UUID eventId,
        /** 角色 ID。 */
        UUID roleId,
        /** 角色编码（如 admin、operator 等）。 */
        String roleCode,
        /** 变更前的权限摘要哈希（SHA-256，包含菜单 ID 和权限 JSON）。 */
        String oldPermissionHash,
        /** 变更后的权限摘要哈希。 */
        String newPermissionHash,
        /** 操作人 ID（修改权限的管理员）。 */
        UUID operatorId,
        /** 事件发生时间。 */
        LocalDateTime occurredAt,
        /** 链路追踪 ID（从 MDC 提取，可为 null）。 */
        String traceId) {
}
