package com.colonel.saas.service;

import com.colonel.saas.domain.user.application.UserPermissionCacheApplicationService;
import com.colonel.saas.service.ShortTtlCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 用户权限缓存失效入口测试（迁移到 DDD Application）。
 *
 * <p>DDD-COMPLETE-100-USER-06：测试对象从 UserPermissionCacheService
 * 迁移到 UserPermissionCacheApplicationService。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserPermissionCacheServiceTest {

    @Mock
    private ShortTtlCacheService shortTtlCacheService;

    private UserPermissionCacheApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new UserPermissionCacheApplicationService(shortTtlCacheService);
    }

    @Test
    void invalidateUser_shouldEvictBothUserAndDataScopeCaches() {
        UUID userId = UUID.randomUUID();
        applicationService.invalidateUser(userId);
        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheApplicationService.USER_PERMISSION_PREFIX + userId);
        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheApplicationService.USER_DATA_SCOPE_PREFIX + userId);
    }

    @Test
    void invalidateUser_shouldBeNoOpWhenUserIdIsNull() {
        applicationService.invalidateUser(null);
        verify(shortTtlCacheService, never()).evictByPrefix(anyString());
    }

    @Test
    void invalidateRole_shouldEvictRolePrefix() {
        UUID roleId = UUID.randomUUID();
        applicationService.invalidateRole(roleId);
        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheApplicationService.ROLE_PERMISSION_PREFIX + roleId);
    }

    @Test
    void invalidateRole_shouldBeNoOpWhenRoleIdIsNull() {
        applicationService.invalidateRole(null);
        verify(shortTtlCacheService, never()).evictByPrefix(anyString());
    }

    @Test
    void invalidateDataScopeForGroupChange_shouldEvictOldNewAndGlobalPrefixes() {
        UUID oldGroup = UUID.randomUUID();
        UUID newGroup = UUID.randomUUID();
        applicationService.invalidateDataScopeForGroupChange(oldGroup, newGroup);
        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheApplicationService.USER_DATA_SCOPE_PREFIX + "dept:" + oldGroup);
        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheApplicationService.USER_DATA_SCOPE_PREFIX + "dept:" + newGroup);
        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheApplicationService.USER_DATA_SCOPE_PREFIX);
    }

    @Test
    void invalidateDataScopeForGroupChange_shouldEvictGlobalPrefixWhenGroupsAreNull() {
        applicationService.invalidateDataScopeForGroupChange(null, null);
        verify(shortTtlCacheService, times(1)).evictByPrefix(UserPermissionCacheApplicationService.USER_DATA_SCOPE_PREFIX);
    }

    @Test
    void invalidateAllRolePermissions_shouldEvictGlobalRolePrefix() {
        applicationService.invalidateAllRolePermissions();
        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheApplicationService.ROLE_PERMISSION_PREFIX);
    }
}