package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserCrudMutationStore.ManagedUser;
import com.colonel.saas.domain.user.port.UserCrudMutationStore.NewUser;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserCrudMutationStoreAdapterTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    private SysUserCrudMutationStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SysUserCrudMutationStoreAdapter(sysUserMapper, sysRoleMapper, sysUserRoleMapper);
    }

    @Test
    void findUser_mapsEntityToManagedUser() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        SysUser entity = new SysUser();
        entity.setId(userId);
        entity.setUsername("alice");
        entity.setRealName("Alice");
        entity.setPhone("13800000000");
        entity.setEmail("a@example.com");
        entity.setDeptId(deptId);
        entity.setStatus(1);
        entity.setForcePasswordChange(true);
        entity.setLastLoginAt(now);
        entity.setCreateTime(now.minusDays(1));
        when(sysUserMapper.selectById(userId)).thenReturn(entity);

        Optional<ManagedUser> result = adapter.findUser(userId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(userId);
        assertThat(result.get().username()).isEqualTo("alice");
        assertThat(result.get().deptId()).isEqualTo(deptId);
        assertThat(result.get().forcePasswordChange()).isTrue();
    }

    @Test
    void saveUser_mapsManagedUserToEntity() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        ManagedUser user = new ManagedUser(
                userId,
                "alice",
                "Alice",
                "13800000000",
                "a@example.com",
                deptId,
                1,
                false,
                now,
                now.minusDays(1),
                0);

        adapter.saveUser(user);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(userId);
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getRealName()).isEqualTo("Alice");
        assertThat(captor.getValue().getDeptId()).isEqualTo(deptId);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void createSupportMethods_delegateAndMapPersistenceTypes() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        SysUser existing = new SysUser();
        existing.setId(userId);
        existing.setUsername("alice");
        existing.setDeptId(deptId);
        existing.setStatus(1);
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setRoleCode("biz_staff");
        role.setStatus(1);
        when(sysUserMapper.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(sysRoleMapper.selectBatchIds(List.of(roleId))).thenReturn(List.of(role));

        Optional<ManagedUser> user = adapter.findByUsername("alice");
        List<?> roles = adapter.findRolesByIds(List.of(roleId));

        assertThat(user).isPresent();
        assertThat(user.get().username()).isEqualTo("alice");
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0)).hasFieldOrPropertyWithValue("roleCode", "biz_staff");
    }

    @Test
    void insertUserAndReplaceUserRoles_mapToEntities() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        NewUser user = new NewUser(
                userId,
                "alice",
                "encoded",
                "Alice",
                "13800000000",
                "a@example.com",
                deptId,
                2,
                true,
                "alice");

        adapter.insertUser(user);
        adapter.replaceUserRoles(userId, List.of(roleId));

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getId()).isEqualTo(userId);
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded");
        assertThat(userCaptor.getValue().getChannelCode()).isEqualTo("alice");
        ArgumentCaptor<SysUserRole> roleCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(sysUserRoleMapper).deleteByUserIdPhysical(userId);
        verify(sysUserRoleMapper).insert(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(roleCaptor.getValue().getRoleId()).isEqualTo(roleId);
    }

    @Test
    void restoreUser_reusesExistingIdAndOverwritesCredentialsAndProfile() {
        UUID existingUserId = UUID.randomUUID();
        NewUser user = new NewUser(
                UUID.randomUUID(),
                "玄同",
                "encoded",
                "新用户",
                null,
                "new@example.com",
                null,
                2,
                true,
                "user");
        when(sysUserMapper.restoreById(any(SysUser.class))).thenReturn(1);

        boolean restored = adapter.restoreUser(existingUserId, user);

        assertThat(restored).isTrue();
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).restoreById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existingUserId);
        assertThat(captor.getValue().getUsername()).isEqualTo("玄同");
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded");
        assertThat(captor.getValue().getRealName()).isEqualTo("新用户");
        assertThat(captor.getValue().getStatus()).isEqualTo(2);
        assertThat(captor.getValue().getForcePasswordChange()).isTrue();
        assertThat(captor.getValue().getChannelCode()).isEqualTo("user");
    }

    @Test
    void deleteAndPasswordOperations_delegateToMappers() {
        UUID userId = UUID.randomUUID();

        adapter.deleteUserRoles(userId);
        adapter.softDeleteUser(userId);
        adapter.updatePassword(userId, "encoded", true);

        verify(sysUserRoleMapper).deleteByUserIdPhysical(userId);
        verify(sysUserMapper).softDeleteById(userId);
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(userId);
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded");
        assertThat(captor.getValue().getForcePasswordChange()).isTrue();
    }

    @Test
    void findRoleIdsByUserId_filtersNullsAndDeduplicates() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        SysUserRole first = relation(userId, roleId);
        SysUserRole duplicated = relation(userId, roleId);
        SysUserRole missingRole = relation(userId, null);
        when(sysUserRoleMapper.findByUserId(userId))
                .thenReturn(List.of(first, duplicated, missingRole));

        List<UUID> roleIds = adapter.findRoleIdsByUserId(userId);

        assertThat(roleIds).containsExactly(roleId);
    }

    private static SysUserRole relation(UUID userId, UUID roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setId(UUID.randomUUID());
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }
}
