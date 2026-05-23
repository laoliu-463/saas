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
