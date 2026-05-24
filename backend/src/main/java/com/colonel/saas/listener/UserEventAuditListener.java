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
 * 用户域事件审计：事务提交后写入操作日志，便于追踪。
 */
@Component
public class UserEventAuditListener {

    private final OperationLogService operationLogService;

    public UserEventAuditListener(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

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
