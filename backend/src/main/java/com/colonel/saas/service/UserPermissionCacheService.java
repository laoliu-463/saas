package com.colonel.saas.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 用户/角色/数据范围相关短 TTL 缓存失效入口。
 */
@Service
public class UserPermissionCacheService {

    public static final String USER_PERMISSION_PREFIX = "user:permission:";
    public static final String USER_DATA_SCOPE_PREFIX = "user:datascope:";
    public static final String ROLE_PERMISSION_PREFIX = "role:permission:";

    private final ShortTtlCacheService shortTtlCacheService;

    public UserPermissionCacheService(ShortTtlCacheService shortTtlCacheService) {
        this.shortTtlCacheService = shortTtlCacheService;
    }

    public void invalidateUser(UUID userId) {
        if (userId == null) {
            return;
        }
        shortTtlCacheService.evictByPrefix(USER_PERMISSION_PREFIX + userId);
        shortTtlCacheService.evictByPrefix(USER_DATA_SCOPE_PREFIX + userId);
    }

    public void invalidateRole(UUID roleId) {
        if (roleId == null) {
            return;
        }
        shortTtlCacheService.evictByPrefix(ROLE_PERMISSION_PREFIX + roleId);
    }

    /**
     * 组别变更时，失效该组下数据范围相关缓存（粗粒度前缀，避免逐用户查库）。
     */
    public void invalidateDataScopeForGroupChange(UUID oldGroupId, UUID newGroupId) {
        if (oldGroupId != null) {
            shortTtlCacheService.evictByPrefix(USER_DATA_SCOPE_PREFIX + "dept:" + oldGroupId);
        }
        if (newGroupId != null) {
            shortTtlCacheService.evictByPrefix(USER_DATA_SCOPE_PREFIX + "dept:" + newGroupId);
        }
        shortTtlCacheService.evictByPrefix(USER_DATA_SCOPE_PREFIX);
    }

    public void invalidateAllRolePermissions() {
        shortTtlCacheService.evictByPrefix(ROLE_PERMISSION_PREFIX);
    }
}
