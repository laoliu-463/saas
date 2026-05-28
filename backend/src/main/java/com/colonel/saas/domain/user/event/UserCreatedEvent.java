package com.colonel.saas.domain.user.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户已创建事件（新员工入职）。
 *
 * <p>当管理员在系统中创建新用户账号时触发，记录用户基本信息、角色、组织归属等。
 * 该事件用于通知相关模块初始化用户关联数据（如权限缓存、数据范围等）。</p>
 */
public record UserCreatedEvent(
        /** 事件唯一标识。 */
        UUID eventId,
        /** 用户 ID。 */
        UUID userId,
        /** 用户登录名。 */
        String username,
        /** 用户真实姓名。 */
        String realName,
        /** 分配的角色 ID。 */
        UUID roleId,
        /** 角色编码（如 admin、operator 等）。 */
        String roleCode,
        /** 所属用户组 ID。 */
        UUID groupId,
        /** 所属部门 ID（对应 sys_dept 组织单元）。 */
        UUID deptId,
        /** 用户状态标签（如 ACTIVE、PENDING_ACTIVATION 等）。 */
        String status,
        /** 操作人 ID（创建用户的管理员）。 */
        UUID operatorId,
        /** 事件发生时间。 */
        LocalDateTime occurredAt,
        /** 链路追踪 ID（从 MDC 提取，可为 null）。 */
        String traceId) {
}
