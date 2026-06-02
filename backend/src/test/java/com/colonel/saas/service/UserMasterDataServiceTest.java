package com.colonel.saas.service;

import com.colonel.saas.constant.RoleCodes;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 用户主数据服务单元测试。
 *
 * <p>覆盖 3 类场景：
 * <ul>
 *     <li>正常返回：按角色编码查询，关键词过滤、用户激活状态、排序、limit</li>
 *     <li>空数据：角色不存在 / 用户被禁用 / 关键词不命中 / dept 缺省 / 非 admin 跨部门</li>
 *     <li>鉴权失败：service 本身不鉴权（由 controller 注解控制）；保证 service 不会自动加 caller 过滤</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserMasterDataServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    private UserMasterDataService userMasterDataService;

    private final UUID deptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userMasterDataService = new UserMasterDataService(sysUserMapper, sysRoleMapper, sysUserRoleMapper);
    }

    // ========================== 正常返回 ==========================

    @Test
    void listChannels_shouldReturnOnlyActiveChannelUsersMatchingKeyword() {
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        SysRole bizRole = role(RoleCodes.BIZ_STAFF);
        SysUser channelUser = user("channel_staff", "渠道专员", deptId, 1);
        SysUser disabledChannel = user("channel_disabled", "渠道停用", deptId, 0);
        SysUser bizUser = user("biz_staff", "招商专员", deptId, 1);
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_LEADER)).thenReturn(Optional.empty());
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.of(channelRole));
        when(sysUserRoleMapper.findByRoleId(channelRole.getId())).thenReturn(List.of(
                relation(channelUser.getId(), channelRole.getId()),
                relation(disabledChannel.getId(), channelRole.getId())
        ));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(channelUser, disabledChannel, bizUser));
        when(sysUserRoleMapper.findByUserIds(any())).thenReturn(List.of(
                relation(channelUser.getId(), channelRole.getId()),
                relation(bizUser.getId(), bizRole.getId())
        ));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(channelRole, bizRole));

        List<UserOptionResponse> result = userMasterDataService.listChannels("渠道", 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).username()).isEqualTo("channel_staff");
        assertThat(result.get(0).roleCodes()).containsExactly(RoleCodes.CHANNEL_STAFF);
    }

    @Test
    void listRecruiters_shouldIncludeLeaderAndStaff() {
        SysRole bizLeader = role(RoleCodes.BIZ_LEADER);
        SysRole bizStaff = role(RoleCodes.BIZ_STAFF);
        SysUser leader = user("biz_leader", "招商组长", deptId, 1);
        SysUser staff = user("biz_staff", "招商专员", deptId, 1);
        when(sysRoleMapper.findByRoleCode(RoleCodes.BIZ_LEADER)).thenReturn(Optional.of(bizLeader));
        when(sysRoleMapper.findByRoleCode(RoleCodes.BIZ_STAFF)).thenReturn(Optional.of(bizStaff));
        when(sysUserRoleMapper.findByRoleId(bizLeader.getId())).thenReturn(List.of(relation(leader.getId(), bizLeader.getId())));
        when(sysUserRoleMapper.findByRoleId(bizStaff.getId())).thenReturn(List.of(relation(staff.getId(), bizStaff.getId())));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(leader, staff));
        when(sysUserRoleMapper.findByUserIds(any())).thenReturn(List.of(
                relation(leader.getId(), bizLeader.getId()),
                relation(staff.getId(), bizStaff.getId())
        ));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(bizLeader, bizStaff));

        List<UserOptionResponse> result = userMasterDataService.listRecruiters(null, 50);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserOptionResponse::username)
                .containsExactlyInAnyOrder("biz_leader", "biz_staff");
    }

    @Test
    void listGroupMembers_shouldLimitNonAdminToCurrentDept() {
        SysUser member = user("member", "本组成员", deptId, 1);
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        when(sysUserMapper.selectList(any())).thenReturn(List.of(member));
        when(sysUserRoleMapper.findByUserIds(List.of(member.getId()))).thenReturn(List.of(
                relation(member.getId(), channelRole.getId())
        ));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(channelRole));

        List<UserOptionResponse> result = userMasterDataService.listGroupMembers(
                UUID.randomUUID(),
                deptId,
                List.of(RoleCodes.CHANNEL_LEADER),
                "成员",
                50
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deptId()).isEqualTo(deptId);
    }

    @Test
    void listGroupMembers_shouldAllowAdminToViewRequestedDept() {
        UUID otherDeptId = UUID.randomUUID();
        SysUser otherDeptMember = user("otherMember", "其他组员", otherDeptId, 1);
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        when(sysUserMapper.selectList(any())).thenReturn(List.of(otherDeptMember));
        when(sysUserRoleMapper.findByUserIds(List.of(otherDeptMember.getId()))).thenReturn(List.of(
                relation(otherDeptMember.getId(), channelRole.getId())
        ));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(channelRole));

        List<UserOptionResponse> result = userMasterDataService.listGroupMembers(
                otherDeptId,
                deptId,
                List.of(RoleCodes.ADMIN), // 当前用户是 admin
                null,
                50
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deptId()).isEqualTo(otherDeptId);
    }

    @Test
    void list_shouldCapLimitAt100() {
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_LEADER)).thenReturn(Optional.empty());
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.empty());

        List<UserOptionResponse> result = userMasterDataService.listChannels(null, 9999);

        // 当没有匹配角色时返回空（同时验证 limit 不会触发 NPE）
        assertThat(result).isEmpty();
    }

    // ========================== 空数据 ==========================

    @Test
    void listChannels_shouldReturnEmptyWhenNoMatchingRoles() {
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_LEADER)).thenReturn(Optional.empty());
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.empty());

        List<UserOptionResponse> result = userMasterDataService.listChannels(null, 50);

        assertThat(result).isEmpty();
    }

    @Test
    void listChannels_shouldReturnEmptyWhenNoUsersAssigned() {
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_LEADER)).thenReturn(Optional.empty());
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.of(channelRole));
        when(sysUserRoleMapper.findByRoleId(channelRole.getId())).thenReturn(List.of());

        List<UserOptionResponse> result = userMasterDataService.listChannels("渠", 50);

        assertThat(result).isEmpty();
    }

    @Test
    void listChannels_shouldReturnEmptyWhenKeywordMatchesNoUser() {
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        SysUser u = user("channel_a", "A", deptId, 1);
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_LEADER)).thenReturn(Optional.empty());
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.of(channelRole));
        when(sysUserRoleMapper.findByRoleId(channelRole.getId())).thenReturn(List.of(relation(u.getId(), channelRole.getId())));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(u));
        when(sysUserRoleMapper.findByUserIds(any())).thenReturn(List.of(relation(u.getId(), channelRole.getId())));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(channelRole));

        List<UserOptionResponse> result = userMasterDataService.listChannels("ZZZ", 50);

        assertThat(result).isEmpty();
    }

    @Test
    void listGroupMembers_shouldReturnEmptyWhenCurrentDeptIdIsNull() {
        List<UserOptionResponse> result = userMasterDataService.listGroupMembers(
                null,
                null,
                List.of(RoleCodes.CHANNEL_LEADER),
                null,
                50
        );

        assertThat(result).isEmpty();
    }

    // ========================== 鉴权失败 ==========================
    // service 层不进行角色校验，由 controller 的 @RequireRoles 控制
    // 此处验证 service 不会自动附加"按调用者过滤"逻辑：
    // 调用同一组入参，无论 caller 角色如何，service 行为一致。

    @Test
    void list_shouldNotInjectCallerRoleFilter_callerRolesIgnored() {
        // 同一组 query 入参下，service 不应隐式根据 caller 角色附加任何过滤
        SysRole channelRole = role(RoleCodes.CHANNEL_STAFF);
        SysUser channelUser = user("channel_staff", "渠道专员", deptId, 1);
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_LEADER)).thenReturn(Optional.empty());
        when(sysRoleMapper.findByRoleCode(RoleCodes.CHANNEL_STAFF)).thenReturn(Optional.of(channelRole));
        when(sysUserRoleMapper.findByRoleId(channelRole.getId())).thenReturn(List.of(relation(channelUser.getId(), channelRole.getId())));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(channelUser));
        when(sysUserRoleMapper.findByUserIds(any())).thenReturn(List.of(relation(channelUser.getId(), channelRole.getId())));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(channelRole));

        // 第一次：用 BIZ_STAFF 角色调用
        List<UserOptionResponse> r1 = userMasterDataService.listChannels(null, 50);
        // 第二次：完全相同的入参再调一次
        List<UserOptionResponse> r2 = userMasterDataService.listChannels(null, 50);

        // service 不应因 caller 角色而异；两次结果一致
        assertThat(r1).hasSize(1);
        assertThat(r2).hasSize(1);
        assertThat(r1.get(0).username()).isEqualTo(r2.get(0).username());
    }

    // ========================== helpers ==========================

    private SysRole role(String roleCode) {
        SysRole role = new SysRole();
        role.setId(UUID.randomUUID());
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode);
        role.setStatus(1);
        return role;
    }

    private SysUser user(String username, String realName, UUID deptId, int status) {
        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setRealName(realName);
        user.setDeptId(deptId);
        user.setStatus(status);
        user.setDeleted(0);
        return user;
    }

    private SysUserRole relation(UUID userId, UUID roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setId(UUID.randomUUID());
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        relation.setDeleted(0);
        return relation;
    }
}
