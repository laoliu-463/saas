package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.api.AuthorizationPrincipal;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysAuthorizationPrincipalStoreAdapterTest {

    @Mock
    private SysUserMapper sysUserMapper;

    private SysAuthorizationPrincipalStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SysAuthorizationPrincipalStoreAdapter(sysUserMapper);
    }

    @Test
    void loadLoginEligible_whenUserIdIsNull_shouldReturnEmptyWithoutQuery() {
        assertThat(adapter.loadLoginEligible(null)).isEmpty();

        verifyNoInteractions(sysUserMapper);
    }

    @Test
    void loadLoginEligible_whenUserDoesNotExist_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        when(sysUserMapper.selectById(userId)).thenReturn(null);

        assertThat(adapter.loadLoginEligible(userId)).isEmpty();
    }

    @Test
    void loadLoginEligible_whenUserIsActive_shouldMapPrincipal() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        SysUser user = user(userId, deptId, "alice", SysUserStatus.ACTIVE, 7L);
        when(sysUserMapper.selectById(userId)).thenReturn(user);

        Optional<AuthorizationPrincipal> result = adapter.loadLoginEligible(userId);

        assertThat(result).contains(new AuthorizationPrincipal(
                userId, deptId, "alice", 7L, false));
    }

    @Test
    void loadLoginEligible_whenUserIsPendingActivation_shouldMapPendingPrincipal() {
        UUID userId = UUID.randomUUID();
        SysUser user = user(
                userId, null, "pending", SysUserStatus.PENDING_ACTIVATION, 3L);
        when(sysUserMapper.selectById(userId)).thenReturn(user);

        Optional<AuthorizationPrincipal> result = adapter.loadLoginEligible(userId);

        assertThat(result).contains(new AuthorizationPrincipal(
                userId, null, "pending", 3L, true));
    }

    @Test
    void loadLoginEligible_whenUserIsDeleted_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        SysUser user = user(userId, null, "alice", SysUserStatus.ACTIVE, 1L);
        user.setDeleted(1);
        when(sysUserMapper.selectById(userId)).thenReturn(user);

        assertThat(adapter.loadLoginEligible(userId)).isEmpty();
    }

    @Test
    void loadLoginEligible_whenUserIsDisabled_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        SysUser user = user(userId, null, "alice", SysUserStatus.DISABLED, 1L);
        when(sysUserMapper.selectById(userId)).thenReturn(user);

        assertThat(adapter.loadLoginEligible(userId)).isEmpty();
    }

    @Test
    void loadLoginEligible_whenAuthorizationVersionIsNull_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        SysUser user = user(userId, null, "alice", SysUserStatus.ACTIVE, null);
        when(sysUserMapper.selectById(userId)).thenReturn(user);

        assertThat(adapter.loadLoginEligible(userId)).isEmpty();
    }

    @Test
    void loadLoginEligible_whenAuthorizationVersionIsZero_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        SysUser user = user(userId, null, "alice", SysUserStatus.ACTIVE, 0L);
        when(sysUserMapper.selectById(userId)).thenReturn(user);

        assertThat(adapter.loadLoginEligible(userId)).isEmpty();
    }

    private static SysUser user(
            UUID userId,
            UUID deptId,
            String username,
            int status,
            Long authzVersion) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setDeptId(deptId);
        user.setUsername(username);
        user.setStatus(status);
        user.setAuthzVersion(authzVersion);
        return user;
    }
}
