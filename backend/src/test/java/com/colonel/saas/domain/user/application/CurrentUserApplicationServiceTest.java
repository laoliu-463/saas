package com.colonel.saas.domain.user.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.service.UserDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserApplicationServiceTest {

    @Mock
    private UserDomainService userDomainService;

    private CurrentUserApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new CurrentUserApplicationService(userDomainService);
    }

    @Test
    void currentUser_shouldNormalizeMissingRoleCodesToEmptyList() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        CurrentUserResponse response = new CurrentUserResponse(
                userId,
                "channel_staff",
                "渠道专员",
                deptId,
                1,
                "self",
                List.of(),
                Map.of(),
                1,
                false
        );
        when(userDomainService.getCurrentUser(userId, deptId, DataScope.PERSONAL, List.of()))
                .thenReturn(response);

        assertThat(applicationService.currentUser(userId, deptId, DataScope.PERSONAL, null))
                .isSameAs(response);
    }

    @Test
    void checkPermission_shouldNormalizeMissingRoleCodesToEmptyList() {
        UUID userId = UUID.randomUUID();
        CheckPermissionRequest request = new CheckPermissionRequest("product", "audit");
        CheckPermissionResponse response = new CheckPermissionResponse("product", "audit", false);
        when(userDomainService.checkPermission(userId, List.of(), request)).thenReturn(response);

        assertThat(applicationService.checkPermission(userId, null, request)).isSameAs(response);
    }

    @Test
    void changePassword_shouldUseCurrentUserOnly() {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("old-pass", "new-pass-123");

        applicationService.changePassword(userId, request);

        verify(userDomainService).changePassword(userId, request);
    }

    @Test
    void dataScope_shouldDelegateRequestContext() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UserDataScopeResponse response = new UserDataScopeResponse("group", 2, List.of(userId));
        when(userDomainService.getUserDataScope(userId, deptId, DataScope.DEPT)).thenReturn(response);

        assertThat(applicationService.dataScope(userId, deptId, DataScope.DEPT)).isSameAs(response);
    }
}
