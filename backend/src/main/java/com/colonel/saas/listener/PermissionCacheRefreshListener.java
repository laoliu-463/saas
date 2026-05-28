package com.colonel.saas.listener;

import com.colonel.saas.domain.user.event.RolePermissionUpdatedEvent;
import com.colonel.saas.domain.user.event.UserDisabledEvent;
import com.colonel.saas.domain.user.event.UserGroupChangedEvent;
import com.colonel.saas.service.UserPermissionCacheService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 用户域事件触发的权限/数据范围缓存失效监听器。
 * <p>
 * 监听用户域的变更事件，在事务提交后自动清除对应的权限缓存，
 * 确保用户权限变更（禁用、换组、角色权限更新）后，后续请求能获取最新权限。
 * </p>
 * <p>
 * 监听的事件类型：
 * <ul>
 *   <li>{@link UserDisabledEvent} - 用户被禁用时，清除该用户的权限缓存</li>
 *   <li>{@link UserGroupChangedEvent} - 用户组别变更时，清除该用户缓存并刷新旧/新组的数据范围</li>
 *   <li>{@link RolePermissionUpdatedEvent} - 角色权限更新时，清除该角色缓存并刷新全局角色权限</li>
 * </ul>
 * </p>
 * <p>
 * 所有事件处理器均使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}，
 * 仅在事务成功提交后执行，避免事务回滚时误清缓存。
 * </p>
 *
 * @see UserPermissionCacheService
 */
@Component
public class PermissionCacheRefreshListener {

    /** 用户权限缓存服务 */
    private final UserPermissionCacheService userPermissionCacheService;

    /**
     * 构造函数，注入依赖。
     *
     * @param userPermissionCacheService 用户权限缓存服务
     */
    public PermissionCacheRefreshListener(UserPermissionCacheService userPermissionCacheService) {
        this.userPermissionCacheService = userPermissionCacheService;
    }

    /**
     * 用户被禁用后，清除该用户的权限缓存。
     *
     * @param event 用户禁用事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDisabled(UserDisabledEvent event) {
        if (event == null) {
            return;
        }
        userPermissionCacheService.invalidateUser(event.userId());
    }

    /**
     * 用户组别变更后，清除该用户缓存并刷新旧/新组的数据范围缓存。
     * <p>
     * 用户换组可能导致其数据范围（self/group/all）发生变化，
     * 因此需要同时清除旧组和新组的数据范围缓存。
     * </p>
     *
     * @param event 用户组别变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserGroupChanged(UserGroupChangedEvent event) {
        if (event == null) {
            return;
        }
        userPermissionCacheService.invalidateUser(event.userId());
        userPermissionCacheService.invalidateDataScopeForGroupChange(event.oldGroupId(), event.newGroupId());
    }

    /**
     * 角色权限更新后，清除该角色缓存并刷新全局角色权限缓存。
     * <p>
     * 角色权限变更会影响所有拥有该角色的用户，因此需要清除该角色缓存
     * 并刷新全局的角色权限映射缓存。
     * </p>
     *
     * @param event 角色权限更新事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRolePermissionUpdated(RolePermissionUpdatedEvent event) {
        if (event == null) {
            return;
        }
        userPermissionCacheService.invalidateRole(event.roleId());
        userPermissionCacheService.invalidateAllRolePermissions();
    }
}
