package com.colonel.saas.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 用户/角色/数据范围相关短 TTL 缓存失效入口。
 * <p>
 * 封装用户权限相关的缓存前缀常量与驱逐方法，当用户信息、角色权限或组织结构发生变化时，
 * 通过前缀驱逐策略使本地短 TTL 缓存失效，确保下一次请求读取到最新数据。
 * </p>
 *
 * <ul>
 *     <li>单用户缓存失效（{@link #invalidateUser}）</li>
 *     <li>单角色缓存失效（{@link #invalidateRole}）</li>
 *     <li>组别变更时的数据范围缓存失效（{@link #invalidateDataScopeForGroupChange}）</li>
 *     <li>全量角色权限缓存失效（{@link #invalidateAllRolePermissions}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>用户域 — 权限缓存管理</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link ShortTtlCacheService} — 本地短 TTL 缓存驱逐引擎</li>
 * </ul>
 *
 * @see ShortTtlCacheService
 */
@Service
public class UserPermissionCacheService {

    /** 用户权限缓存键前缀 */
    public static final String USER_PERMISSION_PREFIX = "user:permission:";
    /** 用户数据范围缓存键前缀 */
    public static final String USER_DATA_SCOPE_PREFIX = "user:datascope:";
    /** 角色权限缓存键前缀 */
    public static final String ROLE_PERMISSION_PREFIX = "role:permission:";

    /** 本地短 TTL 缓存服务 */
    private final ShortTtlCacheService shortTtlCacheService;

    public UserPermissionCacheService(ShortTtlCacheService shortTtlCacheService) {
        this.shortTtlCacheService = shortTtlCacheService;
    }

    /**
     * 失效指定用户的权限与数据范围缓存。
     * <p>同时驱逐该用户的权限缓存和数据范围缓存，适用于用户角色变更、组别变更等场景。</p>
     *
     * @param userId 用户 ID（null 时跳过）
     */
    public void invalidateUser(UUID userId) {
        if (userId == null) {
            return;
        }
        // 驱逐该用户的权限缓存与数据范围缓存
        shortTtlCacheService.evictByPrefix(USER_PERMISSION_PREFIX + userId);
        shortTtlCacheService.evictByPrefix(USER_DATA_SCOPE_PREFIX + userId);
    }

    /**
     * 失效指定角色的权限缓存。
     * <p>适用于角色菜单/操作权限变更后，清除该角色对应的缓存条目。</p>
     *
     * @param roleId 角色 ID（null 时跳过）
     */
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

    /**
     * 失效所有角色的权限缓存。
     * <p>适用于菜单结构或全局权限配置变更后的全量缓存刷新。</p>
     */
    public void invalidateAllRolePermissions() {
        shortTtlCacheService.evictByPrefix(ROLE_PERMISSION_PREFIX);
    }
}
