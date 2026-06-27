package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.dto.SysUserResetPasswordRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.application.SysUserCRUDApplicationA;
import com.colonel.saas.domain.user.application.SysUserCRUDApplicationB;
import com.colonel.saas.domain.user.application.SysUserGroupMembershipApplication;
import com.colonel.saas.domain.user.application.SysUserQueryApplicationService;
import com.colonel.saas.domain.user.application.SysUserRoleAssignmentApplicationService;
import com.colonel.saas.domain.user.application.UserAssignableApplicationService;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceAssignableBoundaryTest {

    @Mock private SysUserCRUDApplicationA sysUserCRUDApplicationA;
    @Mock private SysUserCRUDApplicationB sysUserCRUDApplicationB;
    @Mock private SysUserGroupMembershipApplication sysUserGroupMembershipApplication;
    @Mock private SysUserQueryApplicationService sysUserQueryApplicationService;
    @Mock private UserAssignableApplicationService userAssignableApplicationService;
    @Mock private SysUserRoleAssignmentApplicationService sysUserRoleAssignmentApplicationService;

    private SysUserService service;

    @BeforeEach
    void setUp() {
        service = new SysUserService(
                sysUserCRUDApplicationA,
                sysUserCRUDApplicationB,
                sysUserGroupMembershipApplication,
                sysUserQueryApplicationService,
                userAssignableApplicationService,
                sysUserRoleAssignmentApplicationService);
    }

    @Test
    void findPage_shouldDelegateToUserDomainQueryApplication() {
        UUID currentUserId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();
        SysUserPageRequest request = new SysUserPageRequest(1, 10, "招商", 1, null, null, null, null);
        IPage<SysUserVO> page = new Page<>(1, 10);
        when(sysUserQueryApplicationService.findPage(currentUserId, currentDeptId, DataScope.DEPT, request))
                .thenReturn(page);

        IPage<SysUserVO> result = service.findPage(currentUserId, currentDeptId, DataScope.DEPT, request);

        assertThat(result).isSameAs(page);
        verify(sysUserQueryApplicationService).findPage(currentUserId, currentDeptId, DataScope.DEPT, request);
    }

    @Test
    void findDeptMembers_shouldDelegateToUserDomainQueryApplication() {
        UUID deptId = UUID.randomUUID();
        DeptMemberPageRequest request = new DeptMemberPageRequest(1, 20, "组员", 1, null, null, null);
        IPage<SysUserVO> page = new Page<>(1, 20);
        when(sysUserQueryApplicationService.findDeptMembers(deptId, request)).thenReturn(page);

        IPage<SysUserVO> result = service.findDeptMembers(deptId, request);

        assertThat(result).isSameAs(page);
        verify(sysUserQueryApplicationService).findDeptMembers(deptId, request);
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
    }

    @Test
    void assertRecruiterUser_shouldDelegateToUserDomainApplication() {
        UUID targetUserId = UUID.randomUUID();

        service.assertRecruiterUser(targetUserId);

        verify(userAssignableApplicationService).assertRecruiterUser(targetUserId);
    }

    @Test
    void crudMethods_shouldDelegateToUserDomainCrudApplications() {
        UUID targetUserId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        SysUserVO detail = new SysUserVO();
        when(sysUserCRUDApplicationA.getById(targetUserId, currentUserId, DataScope.DEPT))
                .thenReturn(detail);
        assertThat(service.getById(targetUserId, currentUserId, DataScope.DEPT)).isSameAs(detail);

        SysUserCreateRequest createRequest = new SysUserCreateRequest(
                "newuser", "Passw0rd!", "新用户", "13800000000", "u@example.com",
                null, null, deptId, List.of(roleId));
        SysUserVO created = new SysUserVO();
        when(sysUserCRUDApplicationA.create(createRequest, currentUserId)).thenReturn(created);
        assertThat(service.create(createRequest, currentUserId)).isSameAs(created);

        SysUserUpdateRequest updateRequest = new SysUserUpdateRequest(
                "更新用户", "13900000000", "u2@example.com", 1, null, null, deptId);
        SysUserVO updated = new SysUserVO();
        when(sysUserCRUDApplicationB.update(targetUserId, updateRequest, currentUserId, DataScope.DEPT))
                .thenReturn(updated);
        assertThat(service.update(targetUserId, updateRequest, currentUserId, DataScope.DEPT)).isSameAs(updated);

        service.delete(targetUserId, currentUserId, DataScope.DEPT);
        verify(sysUserCRUDApplicationB).delete(targetUserId, currentUserId, DataScope.DEPT);

        SysUserResetPasswordRequest resetRequest = new SysUserResetPasswordRequest("NewPassw0rd!");
        service.resetPassword(targetUserId, resetRequest, currentUserId, DataScope.DEPT);
        verify(sysUserCRUDApplicationB).resetPassword(targetUserId, resetRequest, currentUserId, DataScope.DEPT);
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
    }
}
