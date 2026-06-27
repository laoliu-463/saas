package com.colonel.saas.domain.user.application;

import com.colonel.saas.service.ShortTtlCacheService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 用户/角色/数据范围缓存失效应用服务（DDD-USER-PERMISSION-CACHE）。
 *
 * <p>封装用户权限相关的缓存前缀常量与驱逐方法。用户信息、角色权限或组织结构发生变化时，
 * 通过前缀驱逐策略使本地短 TTL 缓存失效。</p>
 *
 * <ul>
 *   <li>单用户缓存失效（{@link #invalidateUser}）</li>
 *   <li>单角色缓存失效（{@link #invalidateRole}）</li>
 *   <li>组别变更时的数据范围缓存失效（{@link #invalidateDataScopeForGroupChange}）</li>
 *   <li>全量角色权限缓存失效（{@link #invalidateAllRolePermissions}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>用户域 — 权限缓存管理</p>
 */
@Service
public class UserPermissionCacheApplicationService {

    /** 用户权限缓存键前缀 */
    public static final String USER_PERMISSION_PREFIX = "user:permission:";
    /** 用户数据范围缓存键前缀 */
    public static final String USER_DATA_SCOPE_PREFIX = "user:datascope:";
    /** 角色权限缓存键前缀 */
    public static final String ROLE_PERMISSION_PREFIX = "role:permission:";

    private final ShortTtlCacheService shortTtlCacheService;

    public UserPermissionCacheApplicationService(ShortTtlCacheService shortTtlCacheService) {
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