package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserGroupMembershipStore.GroupMember;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserGroupMembershipStoreAdapterTest {

    @Mock
    private SysUserMapper sysUserMapper;

    private SysUserGroupMembershipStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SysUserGroupMembershipStoreAdapter(sysUserMapper);
    }

    @Test
    void findMember_mapsSysUserToGroupMember() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("alice");
        user.setDeptId(deptId);
        when(sysUserMapper.selectById(userId)).thenReturn(user);

        Optional<GroupMember> member = adapter.findMember(userId);

        assertThat(member).isPresent();
        assertThat(member.get().id()).isEqualTo(userId);
        assertThat(member.get().username()).isEqualTo("alice");
        assertThat(member.get().deptId()).isEqualTo(deptId);
    }

    @Test
    void updateDept_writesOnlyUserIdAndDeptId() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        adapter.updateDept(userId, deptId);

        verify(sysUserMapper).updateDeptById(userId, deptId);
    }

    @Test
    void updateDept_allowsClearingDept() {
        UUID userId = UUID.randomUUID();

        adapter.updateDept(userId, null);

        verify(sysUserMapper).updateDeptById(userId, null);
    }
}
