package com.colonel.saas.listener;

import com.colonel.saas.domain.user.event.RolePermissionUpdatedEvent;
import com.colonel.saas.domain.user.event.UserDisabledEvent;
import com.colonel.saas.domain.user.event.UserGroupChangedEvent;
import com.colonel.saas.service.UserPermissionCacheService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 用户域事件触发的权限/数据范围缓存失效（事务提交后执行）。
 */
@Component
public class PermissionCacheRefreshListener {

    private final UserPermissionCacheService userPermissionCacheService;

    public PermissionCacheRefreshListener(UserPermissionCacheService userPermissionCacheService) {
        this.userPermissionCacheService = userPermissionCacheService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDisabled(UserDisabledEvent event) {
        if (event == null) {
            return;
        }
        userPermissionCacheService.invalidateUser(event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserGroupChanged(UserGroupChangedEvent event) {
        if (event == null) {
            return;
        }
        userPermissionCacheService.invalidateUser(event.userId());
        userPermissionCacheService.invalidateDataScopeForGroupChange(event.oldGroupId(), event.newGroupId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRolePermissionUpdated(RolePermissionUpdatedEvent event) {
        if (event == null) {
            return;
        }
        userPermissionCacheService.invalidateRole(event.roleId());
        userPermissionCacheService.invalidateAllRolePermissions();
    }
}
