package com.colonel.saas.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserPermissionCacheServiceTest {

    @Mock
    private ShortTtlCacheService shortTtlCacheService;

    private UserPermissionCacheService service;

    @BeforeEach
    void setUp() {
        service = new UserPermissionCacheService(shortTtlCacheService);
    }

    @Test
    void invalidateUser_shouldIgnoreNullUserId() {
        service.invalidateUser(null);

        verifyNoInteractions(shortTtlCacheService);
    }

    @Test
    void invalidateUser_shouldEvictPermissionAndDataScopePrefixes() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        service.invalidateUser(userId);

        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheService.USER_PERMISSION_PREFIX + userId);
        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheService.USER_DATA_SCOPE_PREFIX + userId);
    }

    @Test
    void invalidateRole_shouldIgnoreNullRoleId() {
        service.invalidateRole(null);

        verifyNoInteractions(shortTtlCacheService);
    }

    @Test
    void invalidateRole_shouldEvictRolePermissionPrefix() {
        UUID roleId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        service.invalidateRole(roleId);

        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheService.ROLE_PERMISSION_PREFIX + roleId);
    }

    @Test
    void invalidateDataScopeForGroupChange_shouldEvictOldNewAndGlobalPrefixes() {
        UUID oldGroupId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID newGroupId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        service.invalidateDataScopeForGroupChange(oldGroupId, newGroupId);

        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheService.USER_DATA_SCOPE_PREFIX + "dept:" + oldGroupId);
        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheService.USER_DATA_SCOPE_PREFIX + "dept:" + newGroupId);
        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheService.USER_DATA_SCOPE_PREFIX);
    }

    @Test
    void invalidateDataScopeForGroupChange_shouldEvictGlobalPrefixWhenGroupsAreNull() {
        service.invalidateDataScopeForGroupChange(null, null);

        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheService.USER_DATA_SCOPE_PREFIX);
    }

    @Test
    void invalidateAllRolePermissions_shouldEvictRolePermissionPrefix() {
        service.invalidateAllRolePermissions();

        verify(shortTtlCacheService).evictByPrefix(UserPermissionCacheService.ROLE_PERMISSION_PREFIX);
    }
}
