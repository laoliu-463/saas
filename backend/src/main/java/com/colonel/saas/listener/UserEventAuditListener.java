package com.colonel.saas.listener;

import com.colonel.saas.domain.user.event.RolePermissionUpdatedEvent;
import com.colonel.saas.domain.user.event.UserCreatedEvent;
import com.colonel.saas.domain.user.event.UserDisabledEvent;
import com.colonel.saas.domain.user.event.UserGroupChangedEvent;
import com.colonel.saas.service.OperationLogService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 用户域事件审计监听器。
 * <p>
 * 监听用户域的各类事件，在事务提交后写入操作日志表，
 * 实现用户域关键变更的审计追踪。所有审计记录使用"用户域事件"作为操作模块。
 * </p>
 * <p>
 * 监听的事件类型：
 * <ul>
 *   <li>{@link UserCreatedEvent} - 用户创建，记录 userId、status、roleCode、deptId</li>
 *   <li>{@link UserDisabledEvent} - 用户禁用，记录状态变更（oldStatus -> newStatus）</li>
 *   <li>{@link UserGroupChangedEvent} - 用户组别变更，记录旧/新 groupId</li>
 *   <li>{@link RolePermissionUpdatedEvent} - 角色权限更新，记录权限哈希变更</li>
 * </ul>
 * </p>
 * <p>
 * 使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}，
 * 确保仅在业务事务成功提交后才写入审计日志，避免事务回滚时产生虚假审计记录。
 * </p>
 *
 * @see OperationLogService#recordSystemAction
 */
@Component
public class UserEventAuditListener {

    /** 操作日志服务 */
    private final OperationLogService operationLogService;

    /**
     * 构造函数，注入依赖。
     *
     * @param operationLogService 操作日志服务
     */
    public UserEventAuditListener(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 用户创建事件审计。
     * <p>
     * 记录用户创建的详细信息，包括用户 ID、状态、角色编码和部门 ID。
     * </p>
     *
     * @param event 用户创建事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        operationLogService.recordSystemAction(
                event.operatorId(),
                "用户域事件",
                "用户已创建",
                "EVENT",
                "UserCreatedEvent",
                event.eventId() == null ? null : event.eventId().toString(),
                event.username(),
                "userId=" + event.userId() + ", status=" + event.status()
                        + ", roleCode=" + event.roleCode() + ", deptId=" + event.deptId()
        );
    }

    /**
     * 用户禁用事件审计。
     * <p>
     * 记录用户状态变更详情（如 ACTIVE -> DISABLED）。
     * </p>
     *
     * @param event 用户禁用事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDisabled(UserDisabledEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        operationLogService.recordSystemAction(
                event.operatorId(),
                "用户域事件",
                "用户已禁用",
                "EVENT",
                "UserDisabledEvent",
                event.eventId() == null ? null : event.eventId().toString(),
                event.userId().toString(),
                event.oldStatus() + " -> " + event.newStatus()
        );
    }

    /**
     * 用户组别变更事件审计。
     * <p>
     * 记录用户从旧组迁移到新组的详情。
     * </p>
     *
     * @param event 用户组别变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserGroupChanged(UserGroupChangedEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        operationLogService.recordSystemAction(
                event.operatorId(),
                "用户域事件",
                "用户组别变更",
                "EVENT",
                "UserGroupChangedEvent",
                event.eventId() == null ? null : event.eventId().toString(),
                event.userId().toString(),
                "group " + event.oldGroupId() + " -> " + event.newGroupId()
        );
    }

    /**
     * 角色权限更新事件审计。
     * <p>
     * 记录角色权限哈希的变更详情，用于权限变更的追踪和回溯。
     * </p>
     *
     * @param event 角色权限更新事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRolePermissionUpdated(RolePermissionUpdatedEvent event) {
        if (event == null || event.roleId() == null) {
            return;
        }
        operationLogService.recordSystemAction(
                event.operatorId(),
                "用户域事件",
                "角色权限已更新",
                "EVENT",
                "RolePermissionUpdatedEvent",
                event.eventId() == null ? null : event.eventId().toString(),
                event.roleCode(),
                "hash " + event.oldPermissionHash() + " -> " + event.newPermissionHash()
        );
    }
}
