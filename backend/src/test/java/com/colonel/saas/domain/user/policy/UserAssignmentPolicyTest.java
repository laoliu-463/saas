package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserAssignmentPolicy 单测（DDD-USER-MIGRATION-007，Issue #16）。
 *
 * <p>覆盖 assertAssignableUser 和 assertRecruiterUser 两个方法。
 * 行为必须与 SysUserService 旧实现完全一致（被 26 个 SysUserServiceTest 用例间接验证）。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserAssignmentPolicyTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;

    private UserAssignmentPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new UserAssignmentPolicy(sysUserMapper, sysRoleMapper, sysUserRoleMapper);
    }

    // ===== assertAssignableUser =====

    @Test
    void assertAssignableUser_nullTarget_shouldThrow() {
        assertThatThrownBy(() ->
                policy.assertAssignableUser(null, List.of(RoleCodes.ADMIN), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("负责人不能为空");
        verify(sysUserMapper, never()).selectById(any());
    }

    @Test
    void assertAssignableUser_adminUser_shouldNotCheckCrossDept() {
        // ADMIN 角色：可分配所有业务角色，不限部门
        UUID targetUserId = UUID.randomUUID();
        UUID targetDeptId = UUID.randomUUID();  // 目标用户在不同部门
        UUID currentDeptId = UUID.randomUUID();

        SysUser targetUser = new SysUser();
        targetUser.setId(targetUserId);
        targetUser.setDeptId(targetDeptId);
        targetUser.setStatus(1);

        UUID bizLeaderRoleId = UUID.randomUUID();
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(bizLeaderRoleId);
        SysRole bizLeaderRole = new SysRole();
        bizLeaderRole.setId(bizLeaderRoleId);
        bizLeaderRole.setRoleCode(RoleCodes.BIZ_LEADER);
        bizLeaderRole.setStatus(1);

        when(sysUserMapper.selectById(targetUserId)).thenReturn(targetUser);
        when(sysUserRoleMapper.findByUserId(targetUserId)).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(bizLeaderRole));

        // ADMIN 不应该被 deptId 限制
        policy.assertAssignableUser(targetUserId, List.of(RoleCodes.ADMIN), currentDeptId);
    }

    @Test
    void assertAssignableUser_bizLeader_shouldRequireSameDept() {
        // BIZ_LEADER 角色：限同部门
        UUID targetUserId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();

        SysUser targetUser = new SysUser();
        targetUser.setId(targetUserId);
        targetUser.setDeptId(UUID.randomUUID());  // 不同部门

        when(sysUserMapper.selectById(targetUserId)).thenReturn(targetUser);

        assertThatThrownBy(() ->
                policy.assertAssignableUser(targetUserId,
                        List.of(RoleCodes.BIZ_LEADER), currentDeptId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能分配给本组招商下属");
    }

    @Test
    void assertAssignableUser_targetNotFound_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();
        when(sysUserMapper.selectById(targetUserId)).thenReturn(null);

        assertThatThrownBy(() ->
                policy.assertAssignableUser(targetUserId,
                        List.of(RoleCodes.ADMIN), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assertAssignableUser_targetHasNoRoles_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();

        SysUser targetUser = new SysUser();
        targetUser.setId(targetUserId);
        targetUser.setDeptId(currentDeptId);

        when(sysUserMapper.selectById(targetUserId)).thenReturn(targetUser);
        when(sysUserRoleMapper.findByUserId(targetUserId)).thenReturn(new ArrayList<>());

        assertThatThrownBy(() ->
                policy.assertAssignableUser(targetUserId,
                        List.of(RoleCodes.ADMIN), currentDeptId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标负责人未配置可分配角色");
    }

    @Test
    void assertAssignableUser_emptyRoleCodes_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();

        assertThatThrownBy(() ->
                policy.assertAssignableUser(targetUserId, List.of(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前角色不允许分配负责人");
    }

    @Test
    void assertAssignableUser_noMatchingRole_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();

        SysUser targetUser = new SysUser();
        targetUser.setId(targetUserId);
        targetUser.setDeptId(currentDeptId);

        UUID roleId = UUID.randomUUID();
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(roleId);
        SysRole otherRole = new SysRole();
        otherRole.setId(roleId);
        otherRole.setRoleCode("some_other_role");  // 不在可分配列表
        otherRole.setStatus(1);

        when(sysUserMapper.selectById(targetUserId)).thenReturn(targetUser);
        when(sysUserRoleMapper.findByUserId(targetUserId)).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(otherRole));

        assertThatThrownBy(() ->
                policy.assertAssignableUser(targetUserId,
                        List.of(RoleCodes.ADMIN), currentDeptId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能分配给符合规则的招商下属");
    }

    // ===== assertRecruiterUser =====

    @Test
    void assertRecruiterUser_nullTarget_shouldThrow() {
        assertThatThrownBy(() -> policy.assertRecruiterUser(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("assigneeId 不能为空");
    }

    @Test
    void assertRecruiterUser_disabledUser_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();
        SysUser targetUser = new SysUser();
        targetUser.setId(targetUserId);
        targetUser.setStatus(0);  // 禁用

        when(sysUserMapper.selectById(targetUserId)).thenReturn(targetUser);

        assertThatThrownBy(() -> policy.assertRecruiterUser(targetUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户未启用");
    }

    @Test
    void assertRecruiterUser_noRoles_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();
        SysUser targetUser = new SysUser();
        targetUser.setId(targetUserId);
        targetUser.setStatus(1);

        when(sysUserMapper.selectById(targetUserId)).thenReturn(targetUser);
        when(sysUserRoleMapper.findByUserId(targetUserId)).thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> policy.assertRecruiterUser(targetUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户未配置招商角色");
    }

    @Test
    void assertRecruiterUser_bizLeader_shouldPass() {
        UUID targetUserId = UUID.randomUUID();
        SysUser targetUser = new SysUser();
        targetUser.setId(targetUserId);
        targetUser.setStatus(1);

        UUID roleId = UUID.randomUUID();
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(roleId);
        SysRole bizLeaderRole = new SysRole();
        bizLeaderRole.setId(roleId);
        bizLeaderRole.setRoleCode(RoleCodes.BIZ_LEADER);
        bizLeaderRole.setStatus(1);

        when(sysUserMapper.selectById(targetUserId)).thenReturn(targetUser);
        when(sysUserRoleMapper.findByUserId(targetUserId)).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(bizLeaderRole));

        policy.assertRecruiterUser(targetUserId);  // 不抛异常
    }

    @Test
    void assertRecruiterUser_bizStaff_shouldPass() {
        UUID targetUserId = UUID.randomUUID();
        SysUser targetUser = new SysUser();
        targetUser.setId(targetUserId);
        targetUser.setStatus(1);

        UUID roleId = UUID.randomUUID();
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(roleId);
        SysRole bizStaffRole = new SysRole();
        bizStaffRole.setId(roleId);
        bizStaffRole.setRoleCode(RoleCodes.BIZ_STAFF);
        bizStaffRole.setStatus(1);

        when(sysUserMapper.selectById(targetUserId)).thenReturn(targetUser);
        when(sysUserRoleMapper.findByUserId(targetUserId)).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(bizStaffRole));

        policy.assertRecruiterUser(targetUserId);  // 不抛异常
    }

    @Test
    void assertRecruiterUser_channelStaff_shouldThrow() {
        // CHANNEL_STAFF 不在 RECRUITER_ROLE_CODES 内
        UUID targetUserId = UUID.randomUUID();
        SysUser targetUser = new SysUser();
        targetUser.setId(targetUserId);
        targetUser.setStatus(1);

        UUID roleId = UUID.randomUUID();
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(roleId);
        SysRole channelStaffRole = new SysRole();
        channelStaffRole.setId(roleId);
        channelStaffRole.setRoleCode(RoleCodes.CHANNEL_STAFF);
        channelStaffRole.setStatus(1);

        when(sysUserMapper.selectById(targetUserId)).thenReturn(targetUser);
        when(sysUserRoleMapper.findByUserId(targetUserId)).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(channelStaffRole));

        assertThatThrownBy(() -> policy.assertRecruiterUser(targetUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能分配给招商组长、招商专员");
    }
}