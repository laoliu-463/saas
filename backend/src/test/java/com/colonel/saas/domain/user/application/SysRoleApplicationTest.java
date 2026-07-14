package com.colonel.saas.domain.user.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.SysRoleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysRoleApplicationTest {

    @Mock SysRoleMapper sysRoleMapper;
    @Mock SysUserRoleMapper sysUserRoleMapper;
    @Mock OperationLogService operationLogService;
    @Mock AuthorizationVersionApplicationService authorizationVersionService;

    private SysRoleApplication application;
    private final UUID roleId = UUID.randomUUID();
    private SysRole testRole;

    @BeforeEach
    void setUp() {
        application = new SysRoleApplication(
                sysRoleMapper,
                sysUserRoleMapper,
                operationLogService,
                authorizationVersionService);
        testRole = new SysRole();
        testRole.setId(roleId);
        testRole.setRoleCode("test_role");
        testRole.setRoleName("测试角色");
        testRole.setDataScope(1);
        testRole.setStatus(1);
        testRole.setRemark("测试备注");
    }

    @Test
    void findPageReturnsPageWithData() {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(roleId);
        vo.setRoleCode("test_role");
        Page<SysRoleVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);

        when(sysRoleMapper.findPage(any(), any(), any())).thenReturn(page);

        IPage<SysRoleVO> result = application.findPage(1, 10, null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        verify(sysRoleMapper).findPage(any(), any(), any());
    }

    @Test
    void getByIdReturnsRoleWhenExists() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);

        SysRoleVO result = application.getById(roleId);

        assertThat(result.getRoleCode()).isEqualTo("test_role");
        verify(sysRoleMapper).selectById(roleId);
    }

    @Test
    void getByIdThrowsWhenRoleNotFound() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(null);

        assertThatThrownBy(() -> application.getById(roleId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void createSuccessfullyCreatesRole() {
        when(sysRoleMapper.findByRoleCode(anyString())).thenReturn(Optional.empty());
        when(sysRoleMapper.insert(any())).thenAnswer(invocation -> {
            SysRole role = invocation.getArgument(0);
            role.setId(UUID.randomUUID());
            return 1;
        });

        SysRoleCreateRequest request = new SysRoleCreateRequest(
                null, "新角色", 1, 1, "新角色备注");

        SysRoleVO result = application.create(request, UUID.randomUUID());

        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(sysRoleMapper).insert(captor.capture());
        assertThat(captor.getValue().getRoleCode()).matches("^[a-z][a-z0-9_]*$");
        assertThat(captor.getValue().getDataScope()).isEqualTo(1);
        assertThat(result.getRoleName()).isEqualTo("新角色");
        verify(authorizationVersionService, never()).incrementUsersByRole(any(), any(), any());
    }

    @Test
    void createThrowsWhenRoleCodeExists() {
        when(sysRoleMapper.findByRoleCode("existing")).thenReturn(Optional.of(testRole));

        SysRoleCreateRequest request = new SysRoleCreateRequest(
                "existing", "重复角色", 1, 1, null);

        assertThatThrownBy(() -> application.create(request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色编码已存在");
    }

    @Test
    void updateSuccessfullyUpdatesRole() {
        UUID currentUserId = UUID.randomUUID();
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);
        when(sysRoleMapper.findByRoleCode("updated_role")).thenReturn(Optional.empty());

        SysRoleUpdateRequest request = new SysRoleUpdateRequest(
                "updated_role", "更新角色", 2, 1, "更新备注");

        SysRoleVO result = application.update(roleId, request, currentUserId);

        InOrder factThenVersion = inOrder(sysRoleMapper, authorizationVersionService);
        factThenVersion.verify(sysRoleMapper).updateById(any());
        factThenVersion.verify(authorizationVersionService).incrementUsersByRole(
                roleId,
                "ROLE_UPDATED",
                currentUserId);
        assertThat(result.getRoleName()).isEqualTo("更新角色");
        assertThat(result.getDataScope()).isEqualTo(2);
    }

    @Test
    void update_versionFailurePropagatesBeforeAudit() {
        UUID currentUserId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("version failed");
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);
        when(sysRoleMapper.findByRoleCode("updated_role")).thenReturn(Optional.empty());
        doThrow(failure).when(authorizationVersionService).incrementUsersByRole(
                roleId,
                "ROLE_UPDATED",
                currentUserId);

        SysRoleUpdateRequest request = new SysRoleUpdateRequest(
                "updated_role", "更新角色", 2, 1, "更新备注");

        assertThatThrownBy(() -> application.update(roleId, request, currentUserId))
                .isSameAs(failure);

        InOrder factThenVersion = inOrder(sysRoleMapper, authorizationVersionService);
        factThenVersion.verify(sysRoleMapper).updateById(any());
        factThenVersion.verify(authorizationVersionService).incrementUsersByRole(
                roleId,
                "ROLE_UPDATED",
                currentUserId);
        verify(operationLogService, never()).recordSystemAction(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateThrowsWhenRoleNotFound() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(null);

        SysRoleUpdateRequest request = new SysRoleUpdateRequest(
                "code", "名称", 1, 1, null);

        assertThatThrownBy(() -> application.update(roleId, request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void deleteSuccessfullyDeletesRole() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);
        when(sysUserRoleMapper.findByRoleId(roleId)).thenReturn(List.of());

        application.delete(roleId, UUID.randomUUID());

        verify(sysRoleMapper).softDeleteById(roleId);
        verify(authorizationVersionService, never()).incrementUsersByRole(any(), any(), any());
    }

    @Test
    void deleteThrowsWhenRoleNotFound() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(null);

        assertThatThrownBy(() -> application.delete(roleId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void deleteThrowsWhenRoleIsSystemRole() {
        testRole.setRoleCode(RoleCodes.ADMIN);
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);

        assertThatThrownBy(() -> application.delete(roleId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("系统内置角色不允许删除");
    }

    @Test
    void deleteThrowsWhenRoleStillAssigned() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);
        when(sysUserRoleMapper.findByRoleId(roleId)).thenReturn(List.of(new SysUserRole()));

        assertThatThrownBy(() -> application.delete(roleId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色仍被用户使用");
    }

    @Test
    void createThrowsWhenUsingReservedSystemRoleCode() {
        SysRoleCreateRequest request = new SysRoleCreateRequest(
                RoleCodes.BIZ_STAFF, "重复预置", 1, 1, null);

        assertThatThrownBy(() -> application.create(request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能使用系统预置角色编码");
        verify(sysRoleMapper, never()).insert(any());
    }

    @Test
    void updateThrowsWhenChangingSystemRoleCode() {
        testRole.setRoleCode(RoleCodes.BIZ_LEADER);
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);

        SysRoleUpdateRequest request = new SysRoleUpdateRequest(
                "other_leader", "招商组长", 2, 1, null);

        assertThatThrownBy(() -> application.update(roleId, request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("系统内置角色编码不允许修改");
        verify(sysRoleMapper, never()).updateById(any());
    }

    @Test
    void findAllEnabledReturnsEnabledRoles() {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(roleId);
        vo.setStatus(1);

        when(sysRoleMapper.findAll(1)).thenReturn(List.of(vo));

        List<SysRoleVO> result = application.findAllEnabled();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(1);
    }
}
