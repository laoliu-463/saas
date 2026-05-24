package com.colonel.saas.listener;

import com.colonel.saas.domain.user.event.RolePermissionUpdatedEvent;
import com.colonel.saas.domain.user.event.UserDisabledEvent;
import com.colonel.saas.domain.user.event.UserGroupChangedEvent;
import com.colonel.saas.service.UserPermissionCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PermissionCacheRefreshListenerTest {

    @Mock
    private UserPermissionCacheService userPermissionCacheService;

    @InjectMocks
    private PermissionCacheRefreshListener listener;

    @Test
    void onUserDisabled_shouldInvalidateUserCache() {
        UUID userId = UUID.randomUUID();
        listener.onUserDisabled(new UserDisabledEvent(
                UUID.randomUUID(),
                userId,
                "ACTIVE",
                "DISABLED",
                UUID.randomUUID(),
                LocalDateTime.now(),
                "trace"));

        verify(userPermissionCacheService).invalidateUser(userId);
    }

    @Test
    void onUserGroupChanged_shouldInvalidateUserAndDeptScope() {
        UUID userId = UUID.randomUUID();
        UUID oldGroup = UUID.randomUUID();
        UUID newGroup = UUID.randomUUID();
        listener.onUserGroupChanged(new UserGroupChangedEvent(
                UUID.randomUUID(),
                userId,
                oldGroup,
                newGroup,
                oldGroup,
                newGroup,
                UUID.randomUUID(),
                LocalDateTime.now(),
                "trace"));

        verify(userPermissionCacheService).invalidateUser(userId);
        verify(userPermissionCacheService).invalidateDataScopeForGroupChange(oldGroup, newGroup);
    }

    @Test
    void onRolePermissionUpdated_shouldInvalidateRoleCache() {
        UUID roleId = UUID.randomUUID();
        listener.onRolePermissionUpdated(new RolePermissionUpdatedEvent(
                UUID.randomUUID(),
                roleId,
                "channel_lead",
                "old",
                "new",
                UUID.randomUUID(),
                LocalDateTime.now(),
                "trace"));

        verify(userPermissionCacheService).invalidateRole(roleId);
        verify(userPermissionCacheService).invalidateAllRolePermissions();
    }
}
