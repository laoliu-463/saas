package com.colonel.saas.domain.user.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户组别变更事件。
 *
 * <p>当用户被调岗或重新分配到不同用户组时触发。
 * 技术实现上，用户组别通过 {@code sys_user.dept_id} 指向 {@code sys_dept} 组织单元表。
 * 该事件用于通知相关模块更新用户的数据范围和权限上下文。</p>
 */
public record UserGroupChangedEvent(
        /** 事件唯一标识。 */
        UUID eventId,
        /** 用户 ID。 */
        UUID userId,
        /** 原用户组 ID。 */
        UUID oldGroupId,
        /** 新用户组 ID。 */
        UUID newGroupId,
        /** 原部门 ID（对应 sys_dept 表主键）。 */
        UUID oldDeptId,
        /** 新部门 ID。 */
        UUID newDeptId,
        /** 操作人 ID（执行调岗的管理员）。 */
        UUID operatorId,
        /** 事件发生时间。 */
        LocalDateTime occurredAt,
        /** 链路追踪 ID（从 MDC 提取，可为 null）。 */
        String traceId) {
}
