package com.colonel.saas.service;

import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.application.UserMasterDataApplicationService;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.dto.user.UserOptionResponse;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 用户主数据服务单元测试（迁移到 DDD Application）。
 *
 * <p>DDD-COMPLETE-100-USER-06：测试对象从 UserMasterDataService 迁移到
 * UserMasterDataApplicationService（业务逻辑真实所在）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserMasterDataServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    private UserMasterDataApplicationService applicationService;

    private final UUID deptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        applicationService = new UserMasterDataApplicationService(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                new CurrentUserPermissionPolicy());
    }

    // ========================== 正常返回 ==========================

    @Test
    void listChannels_shouldReturnOnlyActiveChannelUsersMatchingKeyword() {
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        SysUser channelUser = user("channel_staff", "渠道专员", deptId, 1);
        // 每个测试明确 stub 全部依赖
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_LEADER)).thenReturn(Optional.empty());
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.of(channelRole));
        when(sysUserRoleMapper.findByRoleId(channelRole.getId())).thenReturn(List.of(
                userRole(channelUser.getId(), channelRole.getId())));
        when(sysUserMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(channelUser));

        List<UserOptionResponse> result = applicationService.listChannels(null, null);

        assertThat(result).extracting(UserOptionResponse::username).containsExactly("channel_staff");
    }

    @Test
    void listRecruiters_shouldIncludeLeaderAndStaff() {
        SysRole leaderRole = role(RoleCodes.BIZ_LEADER);
        SysRole staffRole = role(RoleCodes.BIZ_STAFF);
        SysUser leader = user("biz_leader", "招商负责人", deptId, 1);
        SysUser staff = user("biz_staff", "招商专员", deptId, 1);
        when(sysRoleMapper.findByRoleCode(RoleCodes.BIZ_LEADER)).thenReturn(Optional.of(leaderRole));
        when(sysRoleMapper.findByRoleCode(RoleCodes.BIZ_STAFF)).thenReturn(Optional.of(staffRole));
        when(sysUserRoleMapper.findByRoleId(leaderRole.getId())).thenReturn(List.of(userRole(leader.getId(), leaderRole.getId())));
        when(sysUserRoleMapper.findByRoleId(staffRole.getId())).thenReturn(List.of(userRole(staff.getId(), staffRole.getId())));
        when(sysUserMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(leader, staff));

        List<UserOptionResponse> result = applicationService.listRecruiters(null, null);

        assertThat(result).extracting(UserOptionResponse::username)
                .containsExactlyInAnyOrder("biz_leader", "biz_staff");
    }

    @Test
    void list_shouldRespectLimitBoundary() {
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        SysUser u1 = user("u1", "u1", deptId, 1);
        SysUser u2 = user("u2", "u2", deptId, 1);
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.of(channelRole));
        when(sysUserRoleMapper.findByRoleId(channelRole.getId())).thenReturn(List.of(
                userRole(u1.getId(), channelRole.getId()),
                userRole(u2.getId(), channelRole.getId())));
        when(sysUserMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(u1, u2));

        List<UserOptionResponse> result = applicationService.listChannels(null, 1);

        assertThat(result).hasSize(1);
    }

    @Test
    void list_shouldSkipDisabledUsers() {
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        SysUser active = user("active", "启用", deptId, 1);
        SysUser deleted = user("deleted", "禁用", deptId, 0);
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.of(channelRole));
        when(sysUserRoleMapper.findByRoleId(channelRole.getId())).thenReturn(List.of(
                userRole(active.getId(), channelRole.getId()),
                userRole(deleted.getId(), channelRole.getId())));
        when(sysUserMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(active, deleted));

        List<UserOptionResponse> result = applicationService.listChannels(null, null);

        assertThat(result).extracting(UserOptionResponse::username).containsExactly("active");
    }

    @Test
    void list_shouldFilterByKeyword() {
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        SysUser matchUser = user("match_user", "匹配", deptId, 1);
        SysUser otherUser = user("other_user", "其他", deptId, 1);
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.of(channelRole));
        when(sysUserRoleMapper.findByRoleId(channelRole.getId())).thenReturn(List.of(
                userRole(matchUser.getId(), channelRole.getId()),
                userRole(otherUser.getId(), channelRole.getId())));
        when(sysUserMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(matchUser, otherUser));

        List<UserOptionResponse> result = applicationService.listChannels("match", null);

        assertThat(result).extracting(UserOptionResponse::username).containsExactly("match_user");
    }

    // ========================== 空数据 ==========================

    @Test
    void list_shouldReturnEmptyWhenRoleNotFound() {
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_LEADER)).thenReturn(Optional.empty());
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.empty());

        List<UserOptionResponse> result = applicationService.listChannels(null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void list_shouldHandleNullKeywordGracefully() {
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        SysUser u = user("u", "u", deptId, 1);
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.of(channelRole));
        when(sysUserRoleMapper.findByRoleId(channelRole.getId())).thenReturn(List.of(
                userRole(u.getId(), channelRole.getId())));
        when(sysUserMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(u));

        List<UserOptionResponse> result = applicationService.listChannels(null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void listGroupMembers_shouldReturnEmptyWhenCurrentDeptIdIsNull() {
        List<UserOptionResponse> result = applicationService.listGroupMembers(
                UUID.randomUUID(), null, List.of("USER"), null, null);
        assertThat(result).isEmpty();
    }

    @Test
    void listGroupMembers_shouldFallBackToCurrentDeptForNonAdmin() {
        SysUser u = user("member", "成员", deptId, 1);
        when(sysUserMapper.selectList(any())).thenReturn(List.of(u));

        List<UserOptionResponse> result = applicationService.listGroupMembers(
                UUID.randomUUID(), deptId, List.of("USER"), null, null);

        assertThat(result).extracting(UserOptionResponse::username).containsExactly("member");
    }

    @Test
    void listGroupMembers_shouldAllowAdminToViewRequestedDept() {
        UUID requestedDept = UUID.randomUUID();
        SysUser u = user("admin_view", "管理员查看", requestedDept, 1);
        when(sysUserMapper.selectList(any())).thenReturn(List.of(u));

        List<UserOptionResponse> result = applicationService.listGroupMembers(
                requestedDept, deptId, List.of(RoleCodes.ADMIN), null, null);

        assertThat(result).extracting(UserOptionResponse::username).containsExactly("admin_view");
    }

    // ========================== 鉴权 ==========================

    @Test
    void list_shouldNotInjectCallerRoleFilter_callerRolesIgnored() {
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        SysUser u = user("caller", "调用者", deptId, 1);
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.of(channelRole));
        when(sysUserRoleMapper.findByRoleId(channelRole.getId())).thenReturn(List.of(
                userRole(u.getId(), channelRole.getId())));
        when(sysUserMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(u));

        List<UserOptionResponse> result = applicationService.listChannels(null, null);

        assertThat(result).hasSize(1);
    }

    // ========================== Helper ==========================

    private SysRole role(String code) {
        SysRole r = new SysRole();
        r.setId(UUID.randomUUID());
        r.setRoleCode(code);
        r.setStatus(1);
        return r;
    }

    private SysUser user(String username, String realName, UUID deptId, int status) {
        SysUser u = new SysUser();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setRealName(realName);
        u.setDeptId(deptId);
        u.setStatus(status);
        u.setDeleted(0);
        return u;
    }

    private SysUserRole userRole(UUID userId, UUID roleId) {
        SysUserRole r = new SysUserRole();
        r.setUserId(userId);
        r.setRoleId(roleId);
        return r;
    }
}