package com.colonel.saas.service;

import com.colonel.saas.domain.user.application.UserPermissionCacheApplicationService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 用户/角色/数据范围缓存失效入口（DDD 委派壳）。
 *
 * <p>委派到 {@link UserPermissionCacheApplicationService}，保留旧静态常量与签名
 * 以兼容遗留调用方。</p>
 *
 * @deprecated 请直接注入 {@link UserPermissionCacheApplicationService}
 */
@Service
@Deprecated
public class UserPermissionCacheService {

    public static final String USER_PERMISSION_PREFIX = UserPermissionCacheApplicationService.USER_PERMISSION_PREFIX;
    public static final String USER_DATA_SCOPE_PREFIX = UserPermissionCacheApplicationService.USER_DATA_SCOPE_PREFIX;
    public static final String ROLE_PERMISSION_PREFIX = UserPermissionCacheApplicationService.ROLE_PERMISSION_PREFIX;

    private final UserPermissionCacheApplicationService applicationService;

    public UserPermissionCacheService(UserPermissionCacheApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public void invalidateUser(UUID userId) {
        applicationService.invalidateUser(userId);
    }

    public void invalidateRole(UUID roleId) {
        applicationService.invalidateRole(roleId);
    }

    public void invalidateDataScopeForGroupChange(UUID oldGroupId, UUID newGroupId) {
        applicationService.invalidateDataScopeForGroupChange(oldGroupId, newGroupId);
    }

    public void invalidateAllRolePermissions() {
        applicationService.invalidateAllRolePermissions();
    }
}