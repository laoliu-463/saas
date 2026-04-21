package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.colonel.saas.auth.dto.*;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysUserServiceTest {

    @Spy private SysUserMapper sysUserMapper;
    @Spy private SysRoleMapper sysRoleMapper;
    @Spy private SysUserRoleMapper sysUserRoleMapper;
    @Spy private PasswordEncoder passwordEncoder;
    
    @InjectMocks private SysUserService sysUserService;

    private SysUser testUser;
    private final UUID userId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testUser = new SysUser();
        testUser.setId(userId);
        testUser.setUsername("testadmin");
        testUser.setRealName("测试管理员");
        testUser.setPhone("13800138000");
        testUser.setEmail("admin@test.com");
        testUser.setDeptId(deptId);
        testUser.setStatus(1);
        testUser.setChannelCode("testadmin");
    }

    @Test
    void findPage_returnsPageWithData() {
        SysUserPageRequest request = new SysUserPageRequest(1, 10, null, null, null);
        SysUserVO vo = new SysUserVO();
        vo.setId(userId);
        vo.setUsername("testadmin");
        Page<SysUserVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);

        when(sysUserMapper.findPage(any(), any(), any())).thenReturn(page);
        when(sysUserRoleMapper.findByUserIds(anyList())).thenReturn(Collections.emptyList());

        IPage<SysUserVO> result = sysUserService.findPage(userId, DataScope.ALL, request);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        verify(sysUserMapper).findPage(any(), any(), any());
    }

    @Test
    void getById_returnsUserWhenExists() {
        when(sysUserMapper.selectById(userId)).thenReturn(testUser);
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(Collections.emptyList());

        SysUserVO result = sysUserService.getById(userId, userId, DataScope.ALL);

        assertThat(result.getUsername()).isEqualTo("testadmin");
        verify(sysUserMapper).selectById(userId);
    }

    @Test
    void getById_throwsWhenUserNotFound() {
        when(sysUserMapper.selectById(userId)).thenReturn(null);

        assertThatThrownBy(() -> sysUserService.getById(userId, userId, DataScope.ALL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void create_encryptsPasswordAndInsertsUser() {
        when(sysUserMapper.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("PlainPassword123")).thenReturn("$2a$10$encoded");
        when(sysUserMapper.insert(any())).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return 1;
        });
        
        SysRole mockRole = new SysRole();
        mockRole.setId(roleId);
        mockRole.setStatus(1);
        when(sysRoleMapper.selectBatchIds(anyList())).thenReturn(List.of(mockRole));
        
        when(sysUserRoleMapper.findByUserId(any())).thenReturn(Collections.emptyList());

        SysUserCreateRequest request = new SysUserCreateRequest(
                "newuser", "PlainPassword123", "新用户", deptId, List.of(roleId));

        SysUserVO result = sysUserService.create(request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$encoded");
        assertThat(result.getUsername()).isEqualTo("newuser");
    }

    @Test
    void create_throwsWhenUsernameExists() {
        when(sysUserMapper.findByUsername("existing")).thenReturn(Optional.of(testUser));

        SysUserCreateRequest request = new SysUserCreateRequest(
                "existing", "password123", "测试", deptId, List.of(roleId));

        assertThatThrownBy(() -> sysUserService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    void update_successfullyUpdatesUser() {
        when(sysUserMapper.selectById(userId)).thenReturn(testUser);
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(Collections.emptyList());

        SysUserUpdateRequest request = new SysUserUpdateRequest("新名字", "13900139000", "new@test.com", 1);

        SysUserVO result = sysUserService.update(userId, request, userId, DataScope.ALL);

        verify(sysUserMapper).updateById(any());
        assertThat(result.getRealName()).isEqualTo("新名字");
    }

    @Test
    void update_throwsWhenUserNotFound() {
        when(sysUserMapper.selectById(userId)).thenReturn(null);

        SysUserUpdateRequest request = new SysUserUpdateRequest("新名字", null, null, 1);

        assertThatThrownBy(() -> sysUserService.update(userId, request, userId, DataScope.ALL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void resetPassword_successfullyResetsPassword() {
        when(sysUserMapper.selectById(userId)).thenReturn(testUser);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("$2a$10$newencoded");

        SysUserResetPasswordRequest request = new SysUserResetPasswordRequest("NewPassword123");

        sysUserService.resetPassword(userId, request, userId, DataScope.ALL);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$newencoded");
    }

    @Test
    void delete_successfullyDeletesUser() {
        UUID otherUserId = UUID.randomUUID();
        when(sysUserMapper.selectById(otherUserId)).thenReturn(testUser);
        testUser.setId(otherUserId);

        sysUserService.delete(otherUserId, userId, DataScope.ALL);

        verify(sysUserRoleMapper).delete(org.mockito.ArgumentMatchers.<Wrapper<com.colonel.saas.entity.SysUserRole>>any());
        verify(sysUserMapper).deleteById(otherUserId);
    }

    @Test
    void delete_throwsWhenDeletingCurrentUser() {
        assertThatThrownBy(() -> sysUserService.delete(userId, userId, DataScope.ALL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能删除当前登录用户");
    }

    @Test
    void assignRoles_successfullyAssignsRoles() {
        when(sysUserMapper.selectById(userId)).thenReturn(testUser);
        when(sysRoleMapper.selectBatchIds(anyList())).thenReturn(List.of(new SysRole()));

        SysUserAssignRolesRequest request = new SysUserAssignRolesRequest(List.of(roleId));

        sysUserService.assignRoles(userId, request, userId, DataScope.ALL);

        verify(sysUserRoleMapper).delete(org.mockito.ArgumentMatchers.<Wrapper<com.colonel.saas.entity.SysUserRole>>any());
        verify(sysUserRoleMapper, times(1)).insert(any());
    }
}
