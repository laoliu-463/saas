package com.colonel.saas.auth.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.application.SysUserCRUDApplicationA;
import com.colonel.saas.domain.user.application.SysUserCRUDApplicationB;
import com.colonel.saas.domain.user.application.SysUserGroupMembershipApplication;
import com.colonel.saas.domain.user.application.SysUserRoleAssignmentApplicationService;
import com.colonel.saas.domain.user.application.UserAssignableApplicationService;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.policy.UserChannelCodePolicy;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.service.UserPermissionCacheService;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceAssignableBoundaryTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OperationLogService operationLogService;
    @Mock private UserDomainEventPublisher userDomainEventPublisher;
    @Mock private OrgStructureService orgStructureService;
    @Mock private UserPermissionCacheService userPermissionCacheService;
    @Mock private UserAccessPolicy userAccessPolicy;
    @Mock private UserChannelCodePolicy userChannelCodePolicy;
    @Mock private SysUserCRUDApplicationA sysUserCRUDApplicationA;
    @Mock private SysUserCRUDApplicationB sysUserCRUDApplicationB;
    @Mock private SysUserGroupMembershipApplication sysUserGroupMembershipApplication;
    @Mock private UserAssignableApplicationService userAssignableApplicationService;
    @Mock private SysUserRoleAssignmentApplicationService sysUserRoleAssignmentApplicationService;

    private SysUserService service;

    @BeforeEach
    void setUp() {
        service = new SysUserService(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                passwordEncoder,
                operationLogService,
                userDomainEventPublisher,
                orgStructureService,
                userPermissionCacheService,
                new DataScopePolicy(),
                userAccessPolicy,
                userChannelCodePolicy,
                sysUserCRUDApplicationA,
                sysUserCRUDApplicationB,
                sysUserGroupMembershipApplication,
                userAssignableApplicationService,
                sysUserRoleAssignmentApplicationService);
    }

    @Test
    void findAssignableUsers_shouldDelegateToUserDomainApplication() {
        UUID deptId = UUID.randomUUID();
        SysUserVO user = new SysUserVO();
        user.setId(UUID.randomUUID());
        when(userAssignableApplicationService.findAssignableUsers(
                "招商",
                List.of(RoleCodes.BIZ_LEADER),
                deptId)).thenReturn(List.of(user));

        List<SysUserVO> result = service.findAssignableUsers(
                "招商",
                List.of(RoleCodes.BIZ_LEADER),
                deptId);

        assertThat(result).containsExactly(user);
        verify(userAssignableApplicationService).findAssignableUsers(
                "招商",
                List.of(RoleCodes.BIZ_LEADER),
                deptId);
        verifyNoInteractions(sysUserMapper, sysRoleMapper, sysUserRoleMapper);
    }

    @Test
    void assertAssignableUser_shouldDelegateToUserDomainApplication() {
        UUID targetUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        service.assertAssignableUser(targetUserId, List.of(RoleCodes.BIZ_LEADER), deptId);

        verify(userAssignableApplicationService).assertAssignableUser(
                targetUserId,
                List.of(RoleCodes.BIZ_LEADER),
                deptId);
        verifyNoInteractions(sysUserMapper, sysRoleMapper, sysUserRoleMapper);
    }

    @Test
    void assertRecruiterUser_shouldDelegateToUserDomainApplication() {
        UUID targetUserId = UUID.randomUUID();

        service.assertRecruiterUser(targetUserId);

        verify(userAssignableApplicationService).assertRecruiterUser(targetUserId);
        verifyNoInteractions(sysUserMapper, sysRoleMapper, sysUserRoleMapper);
    }

    @Test
    void assignRoles_shouldDelegateToUserDomainApplication() {
        UUID targetUserId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        com.colonel.saas.auth.dto.SysUserAssignRolesRequest request =
                new com.colonel.saas.auth.dto.SysUserAssignRolesRequest(List.of(UUID.randomUUID()));

        service.assignRoles(targetUserId, request, currentUserId, DataScope.ALL);

        verify(sysUserRoleAssignmentApplicationService).assignRoles(
                targetUserId,
                request,
                currentUserId,
                DataScope.ALL);
        verifyNoInteractions(sysUserMapper, sysRoleMapper, sysUserRoleMapper);
    }
}
