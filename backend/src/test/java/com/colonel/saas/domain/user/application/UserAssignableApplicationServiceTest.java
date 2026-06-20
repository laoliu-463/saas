package com.colonel.saas.domain.user.application;

import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.domain.user.policy.UserAssignmentPolicy;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAssignableApplicationServiceTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;

    private UserAssignableApplicationService service;

    @BeforeEach
    void setUp() {
        UserAssignmentPolicy assignmentPolicy = new UserAssignmentPolicy(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper);
        service = new UserAssignableApplicationService(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                assignmentPolicy);
    }

    @Test
    void findAssignableUsers_adminAllowsAssignableBusinessRolesAcrossDepartments() {
        UUID currentDeptId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID firstRoleId = UUID.randomUUID();
        UUID secondRoleId = UUID.randomUUID();

        SysUser bizLeader = user(firstUserId, UUID.randomUUID(), "biz-leader", "招商组长");
        SysUser opsUser = user(secondUserId, UUID.randomUUID(), "ops", "运营");
        SysUserRole bizLeaderRelation = relation(firstUserId, firstRoleId);
        SysUserRole opsRelation = relation(secondUserId, secondRoleId);

        when(sysUserMapper.selectList(any())).thenReturn(List.of(bizLeader, opsUser));
        when(sysUserRoleMapper.findByUserId(firstUserId)).thenReturn(List.of(bizLeaderRelation));
        when(sysUserRoleMapper.findByUserId(secondUserId)).thenReturn(List.of(opsRelation));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(
                role(firstRoleId, RoleCodes.BIZ_LEADER, 1),
                role(secondRoleId, RoleCodes.OPS_STAFF, 1)
        ));

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

        SysUser sameDeptStaff = user(sameDeptStaffId, currentDeptId, "staff-a", "招商专员A");
        SysUser crossDeptStaff = user(crossDeptStaffId, UUID.randomUUID(), "staff-b", "招商专员B");
        SysUser sameDeptLeader = user(sameDeptLeaderId, currentDeptId, "leader-a", "招商组长A");

        when(sysUserMapper.selectList(any())).thenReturn(List.of(sameDeptStaff, crossDeptStaff, sameDeptLeader));
        when(sysUserRoleMapper.findByUserId(sameDeptStaffId)).thenReturn(List.of(relation(sameDeptStaffId, staffRoleId)));
        when(sysUserRoleMapper.findByUserId(crossDeptStaffId)).thenReturn(List.of(relation(crossDeptStaffId, staffRoleId)));
        when(sysUserRoleMapper.findByUserId(sameDeptLeaderId)).thenReturn(List.of(relation(sameDeptLeaderId, leaderRoleId)));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(
                role(staffRoleId, RoleCodes.BIZ_STAFF, 1),
                role(leaderRoleId, RoleCodes.BIZ_LEADER, 1)
        ));

        List<SysUserVO> result = service.findAssignableUsers(
                null,
                List.of(RoleCodes.BIZ_LEADER),
                currentDeptId);

        assertThat(result).extracting(SysUserVO::getId).containsExactly(sameDeptStaffId);
    }

    private static SysUser user(UUID id, UUID deptId, String username, String realName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setDeptId(deptId);
        user.setUsername(username);
        user.setRealName(realName);
        user.setStatus(1);
        return user;
    }

    private static SysUserRole relation(UUID userId, UUID roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private static SysRole role(UUID id, String roleCode, Integer status) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setStatus(status);
        return role;
    }
}
