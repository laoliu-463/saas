package com.colonel.saas.domain.user.application;

import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.UserAssignmentPolicy;
import com.colonel.saas.domain.user.port.UserAssignmentLookup;
import com.colonel.saas.domain.user.port.UserAssignmentLookup.AssignableRole;
import com.colonel.saas.domain.user.port.UserAssignmentLookup.UserRoleAssignment;
import com.colonel.saas.domain.user.port.UserAssignableCandidateLookup;
import com.colonel.saas.domain.user.port.UserAssignableCandidateLookup.AssignableCandidate;
import com.colonel.saas.vo.SysUserVO;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAssignableApplicationServiceTest {

    @Mock private UserAssignableCandidateLookup candidateLookup;
    @Mock private UserAssignmentLookup assignmentLookup;

    private UserAssignableApplicationService service;

    @BeforeEach
    void setUp() {
        UserAssignmentPolicy assignmentPolicy = new UserAssignmentPolicy(assignmentLookup);
        service = new UserAssignableApplicationService(
                candidateLookup,
                assignmentLookup,
                assignmentPolicy);
    }

    @Test
    void findAssignableUsers_adminAllowsAssignableBusinessRolesAcrossDepartments() {
        UUID currentDeptId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID firstRoleId = UUID.randomUUID();
        UUID secondRoleId = UUID.randomUUID();

        AssignableCandidate bizLeader = candidate(firstUserId, UUID.randomUUID(), "biz-leader", "招商组长");
        AssignableCandidate opsUser = candidate(secondUserId, UUID.randomUUID(), "ops", "运营");
        SysUserVO bizLeaderVo = vo(firstUserId, firstRoleId);

        when(candidateLookup.findActiveCandidates("招商", 20)).thenReturn(List.of(bizLeader, opsUser));
        when(assignmentLookup.findUserRoles(firstUserId)).thenReturn(List.of(relation(firstUserId, firstRoleId)));
        when(assignmentLookup.findUserRoles(secondUserId)).thenReturn(List.of(relation(secondUserId, secondRoleId)));
        when(assignmentLookup.findRolesByIds(any())).thenReturn(Map.of(
                firstRoleId, role(firstRoleId, RoleCodes.BIZ_LEADER, 1),
                secondRoleId, role(secondRoleId, RoleCodes.OPS_STAFF, 1)
        ));
        when(candidateLookup.toVO(eq(bizLeader), any())).thenReturn(bizLeaderVo);

        List<SysUserVO> result = service.findAssignableUsers(
                "招商",
                List.of(RoleCodes.ADMIN),
                currentDeptId);

        assertThat(result).extracting(SysUserVO::getId).containsExactly(firstUserId);
        assertThat(result.get(0).getRoleIds()).containsExactly(firstRoleId);
    }

    @Test
    void findAssignableUsers_bizLeaderOnlyAllowsSameDepartmentBizStaff() {
        UUID currentDeptId = UUID.randomUUID();
        UUID sameDeptStaffId = UUID.randomUUID();
        UUID crossDeptStaffId = UUID.randomUUID();
        UUID sameDeptLeaderId = UUID.randomUUID();
        UUID staffRoleId = UUID.randomUUID();
        UUID leaderRoleId = UUID.randomUUID();

        AssignableCandidate sameDeptStaff = candidate(sameDeptStaffId, currentDeptId, "staff-a", "招商专员A");
        AssignableCandidate crossDeptStaff = candidate(crossDeptStaffId, UUID.randomUUID(), "staff-b", "招商专员B");
        AssignableCandidate sameDeptLeader = candidate(sameDeptLeaderId, currentDeptId, "leader-a", "招商组长A");
        SysUserVO sameDeptStaffVo = vo(sameDeptStaffId, staffRoleId);

        when(candidateLookup.findActiveCandidates(null, 20))
                .thenReturn(List.of(sameDeptStaff, crossDeptStaff, sameDeptLeader));
        when(assignmentLookup.findUserRoles(sameDeptStaffId)).thenReturn(List.of(relation(sameDeptStaffId, staffRoleId)));
        when(assignmentLookup.findUserRoles(crossDeptStaffId)).thenReturn(List.of(relation(crossDeptStaffId, staffRoleId)));
        when(assignmentLookup.findUserRoles(sameDeptLeaderId)).thenReturn(List.of(relation(sameDeptLeaderId, leaderRoleId)));
        when(assignmentLookup.findRolesByIds(any())).thenReturn(Map.of(
                staffRoleId, role(staffRoleId, RoleCodes.BIZ_STAFF, 1),
                leaderRoleId, role(leaderRoleId, RoleCodes.BIZ_LEADER, 1)
        ));
        when(candidateLookup.toVO(eq(sameDeptStaff), any())).thenReturn(sameDeptStaffVo);

        List<SysUserVO> result = service.findAssignableUsers(
                null,
                List.of(RoleCodes.BIZ_LEADER),
                currentDeptId);

        assertThat(result).extracting(SysUserVO::getId).containsExactly(sameDeptStaffId);
    }

    private static AssignableCandidate candidate(UUID id, UUID deptId, String username, String realName) {
        return new AssignableCandidate(id, username, realName, null, null, deptId, 1, false, null, null);
    }

    private static UserRoleAssignment relation(UUID userId, UUID roleId) {
        return new UserRoleAssignment(userId, roleId);
    }

    private static AssignableRole role(UUID id, String roleCode, Integer status) {
        return new AssignableRole(id, roleCode, status);
    }

    private static SysUserVO vo(UUID userId, UUID roleId) {
        SysUserVO vo = new SysUserVO();
        vo.setId(userId);
        vo.setRoleIds(List.of(roleId));
        return vo;
    }
}
