package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.port.UserAssignmentLookup;
import com.colonel.saas.domain.user.port.UserAssignmentLookup.AssignableRole;
import com.colonel.saas.domain.user.port.UserAssignmentLookup.AssignableUser;
import com.colonel.saas.domain.user.port.UserAssignmentLookup.UserRoleAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * 行为必须与 SysUserService 旧实现完全一致（由用户域应用服务和 true-route 委托测试间接验证）。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserAssignmentPolicyTest {

    @Mock private UserAssignmentLookup assignmentLookup;

    private UserAssignmentPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new UserAssignmentPolicy(assignmentLookup);
    }

    @Test
    void assertAssignableUser_nullTarget_shouldThrow() {
        assertThatThrownBy(() ->
                policy.assertAssignableUser(null, List.of(RoleCodes.ADMIN), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("负责人不能为空");
        verify(assignmentLookup, never()).findUser(any());
    }

    @Test
    void assertAssignableUser_adminUser_shouldNotCheckCrossDept() {
        UUID targetUserId = UUID.randomUUID();
        UUID targetDeptId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();
        UUID bizLeaderRoleId = UUID.randomUUID();

        when(assignmentLookup.findUser(targetUserId))
                .thenReturn(Optional.of(user(targetUserId, targetDeptId, 1)));
        when(assignmentLookup.findUserRoles(targetUserId))
                .thenReturn(List.of(relation(targetUserId, bizLeaderRoleId)));
        when(assignmentLookup.findRolesByIds(any()))
                .thenReturn(Map.of(bizLeaderRoleId, role(bizLeaderRoleId, RoleCodes.BIZ_LEADER, 1)));

        policy.assertAssignableUser(targetUserId, List.of(RoleCodes.ADMIN), currentDeptId);
    }

    @Test
    void assertAssignableUser_bizLeader_shouldRequireSameDept() {
        UUID targetUserId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();

        when(assignmentLookup.findUser(targetUserId))
                .thenReturn(Optional.of(user(targetUserId, UUID.randomUUID(), 1)));

        assertThatThrownBy(() ->
                policy.assertAssignableUser(targetUserId,
                        List.of(RoleCodes.BIZ_LEADER), currentDeptId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能分配给本组招商下属");
    }

    @Test
    void assertAssignableUser_targetNotFound_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();
        when(assignmentLookup.findUser(targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                policy.assertAssignableUser(targetUserId,
                        List.of(RoleCodes.ADMIN), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assertAssignableUser_targetHasNoRoles_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();

        when(assignmentLookup.findUser(targetUserId))
                .thenReturn(Optional.of(user(targetUserId, currentDeptId, 1)));
        when(assignmentLookup.findUserRoles(targetUserId)).thenReturn(List.of());

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
        UUID roleId = UUID.randomUUID();

        when(assignmentLookup.findUser(targetUserId))
                .thenReturn(Optional.of(user(targetUserId, currentDeptId, 1)));
        when(assignmentLookup.findUserRoles(targetUserId))
                .thenReturn(List.of(relation(targetUserId, roleId)));
        when(assignmentLookup.findRolesByIds(any()))
                .thenReturn(Map.of(roleId, role(roleId, "some_other_role", 1)));

        assertThatThrownBy(() ->
                policy.assertAssignableUser(targetUserId,
                        List.of(RoleCodes.ADMIN), currentDeptId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能分配给符合规则的招商下属");
    }

    @Test
    void assertRecruiterUser_nullTarget_shouldThrow() {
        assertThatThrownBy(() -> policy.assertRecruiterUser(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("assigneeId 不能为空");
    }

    @Test
    void assertRecruiterUser_disabledUser_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();
        when(assignmentLookup.findUser(targetUserId))
                .thenReturn(Optional.of(user(targetUserId, UUID.randomUUID(), 0)));

        assertThatThrownBy(() -> policy.assertRecruiterUser(targetUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户未启用");
    }

    @Test
    void assertRecruiterUser_noRoles_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();
        when(assignmentLookup.findUser(targetUserId))
                .thenReturn(Optional.of(user(targetUserId, UUID.randomUUID(), 1)));
        when(assignmentLookup.findUserRoles(targetUserId)).thenReturn(List.of());

        assertThatThrownBy(() -> policy.assertRecruiterUser(targetUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户未配置招商角色");
    }

    @Test
    void assertRecruiterUser_bizLeader_shouldPass() {
        UUID targetUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        when(assignmentLookup.findUser(targetUserId))
                .thenReturn(Optional.of(user(targetUserId, UUID.randomUUID(), 1)));
        when(assignmentLookup.findUserRoles(targetUserId))
                .thenReturn(List.of(relation(targetUserId, roleId)));
        when(assignmentLookup.findRolesByIds(any()))
                .thenReturn(Map.of(roleId, role(roleId, RoleCodes.BIZ_LEADER, 1)));

        policy.assertRecruiterUser(targetUserId);
    }

    @Test
    void assertRecruiterUser_bizStaff_shouldPass() {
        UUID targetUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        when(assignmentLookup.findUser(targetUserId))
                .thenReturn(Optional.of(user(targetUserId, UUID.randomUUID(), 1)));
        when(assignmentLookup.findUserRoles(targetUserId))
                .thenReturn(List.of(relation(targetUserId, roleId)));
        when(assignmentLookup.findRolesByIds(any()))
                .thenReturn(Map.of(roleId, role(roleId, RoleCodes.BIZ_STAFF, 1)));

        policy.assertRecruiterUser(targetUserId);
    }

    @Test
    void assertRecruiterUser_channelStaff_shouldThrow() {
        UUID targetUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        when(assignmentLookup.findUser(targetUserId))
                .thenReturn(Optional.of(user(targetUserId, UUID.randomUUID(), 1)));
        when(assignmentLookup.findUserRoles(targetUserId))
                .thenReturn(List.of(relation(targetUserId, roleId)));
        when(assignmentLookup.findRolesByIds(any()))
                .thenReturn(Map.of(roleId, role(roleId, RoleCodes.CHANNEL_STAFF, 1)));

        assertThatThrownBy(() -> policy.assertRecruiterUser(targetUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能分配给招商组长、招商专员");
    }

    private static AssignableUser user(UUID id, UUID deptId, Integer status) {
        return new AssignableUser(id, deptId, status);
    }

    private static UserRoleAssignment relation(UUID userId, UUID roleId) {
        return new UserRoleAssignment(userId, roleId);
    }

    private static AssignableRole role(UUID id, String roleCode, Integer status) {
        return new AssignableRole(id, roleCode, status);
    }
}
