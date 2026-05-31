package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.SysRoleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysRoleServiceTest {

    @Spy private SysRoleMapper sysRoleMapper;
    @Spy private SysUserRoleMapper sysUserRoleMapper;
    @Mock private OperationLogService operationLogService;

    private SysRoleService sysRoleService;

    private SysRole testRole;
    private final UUID roleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sysRoleService = new SysRoleService(sysRoleMapper, sysUserRoleMapper, operationLogService);
        testRole = new SysRole();
        testRole.setId(roleId);
        testRole.setRoleCode("test_role");
        testRole.setRoleName("测试角色");
        testRole.setDataScope(1);
        testRole.setStatus(1);
        testRole.setRemark("测试备注");
    }

    @Test
    void findPage_returnsPageWithData() {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(roleId);
        vo.setRoleCode("test_role");
        Page<SysRoleVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);

        when(sysRoleMapper.findPage(any(), any(), any())).thenReturn(page);

        IPage<SysRoleVO> result = sysRoleService.findPage(1, 10, null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        verify(sysRoleMapper).findPage(any(), any(), any());
    }

    @Test
    void getById_returnsRoleWhenExists() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);

        SysRoleVO result = sysRoleService.getById(roleId);

        assertThat(result.getRoleCode()).isEqualTo("test_role");
        verify(sysRoleMapper).selectById(roleId);
    }

    @Test
    void getById_throwsWhenRoleNotFound() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(null);

        assertThatThrownBy(() -> sysRoleService.getById(roleId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void create_successfullyCreatesRole() {
        when(sysRoleMapper.findByRoleCode(anyString())).thenReturn(Optional.empty());
        when(sysRoleMapper.insert(any())).thenAnswer(invocation -> {
            SysRole role = invocation.getArgument(0);
            role.setId(UUID.randomUUID());
            return 1;
        });

        SysRoleCreateRequest request = new SysRoleCreateRequest(
                null, "新角色", 1, 1, "新角色备注");

        SysRoleVO result = sysRoleService.create(request, UUID.randomUUID());

        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(sysRoleMapper).insert(captor.capture());
        assertThat(captor.getValue().getRoleCode()).matches("^[a-z][a-z0-9_]*$");
        assertThat(captor.getValue().getDataScope()).isEqualTo(1);
        assertThat(result.getRoleName()).isEqualTo("新角色");
    }

    @Test
    void create_throwsWhenRoleCodeExists() {
        when(sysRoleMapper.findByRoleCode("existing")).thenReturn(Optional.of(testRole));

        SysRoleCreateRequest request = new SysRoleCreateRequest(
                "existing", "重复角色", 1, 1, null);

        assertThatThrownBy(() -> sysRoleService.create(request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色编码已存在");
    }

    @Test
    void update_successfullyUpdatesRole() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);
        when(sysRoleMapper.findByRoleCode("updated_role")).thenReturn(Optional.empty());

        SysRoleUpdateRequest request = new SysRoleUpdateRequest(
                "updated_role", "更新角色", 2, 1, "更新备注");

        SysRoleVO result = sysRoleService.update(roleId, request, UUID.randomUUID());

        verify(sysRoleMapper).updateById(any());
        assertThat(result.getRoleName()).isEqualTo("更新角色");
        assertThat(result.getDataScope()).isEqualTo(2);
    }

    @Test
    void update_throwsWhenRoleNotFound() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(null);

        SysRoleUpdateRequest request = new SysRoleUpdateRequest(
                "code", "名称", 1, 1, null);

        assertThatThrownBy(() -> sysRoleService.update(roleId, request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void delete_successfullyDeletesRole() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);
        when(sysUserRoleMapper.findByRoleId(roleId)).thenReturn(List.of());

        sysRoleService.delete(roleId, UUID.randomUUID());

        verify(sysRoleMapper).softDeleteById(roleId);
    }

    @Test
    void delete_throwsWhenRoleNotFound() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(null);

        assertThatThrownBy(() -> sysRoleService.delete(roleId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void delete_throwsWhenRoleIsSystemRole() {
        testRole.setRoleCode(RoleCodes.ADMIN);
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);

        assertThatThrownBy(() -> sysRoleService.delete(roleId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("系统内置角色不允许删除");
    }

    @Test
    void delete_throwsWhenRoleStillAssigned() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);
        when(sysUserRoleMapper.findByRoleId(roleId)).thenReturn(List.of(new com.colonel.saas.entity.SysUserRole()));

        assertThatThrownBy(() -> sysRoleService.delete(roleId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色仍被用户使用");
    }

    @Test
    void create_throwsWhenUsingReservedSystemRoleCode() {
        SysRoleCreateRequest request = new SysRoleCreateRequest(
                RoleCodes.BIZ_STAFF, "重复预置", 1, 1, null);

        assertThatThrownBy(() -> sysRoleService.create(request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能使用系统预置角色编码");
        verify(sysRoleMapper, never()).insert(any());
    }

    @Test
    void update_throwsWhenChangingSystemRoleCode() {
        testRole.setRoleCode(RoleCodes.BIZ_LEADER);
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);

        SysRoleUpdateRequest request = new SysRoleUpdateRequest(
                "other_leader", "招商组长", 2, 1, null);

        assertThatThrownBy(() -> sysRoleService.update(roleId, request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("系统内置角色编码不允许修改");
        verify(sysRoleMapper, never()).updateById(any());
    }

    @Test
    void findAllEnabled_returnsEnabledRoles() {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(roleId);
        vo.setStatus(1);

        when(sysRoleMapper.findAll(1)).thenReturn(List.of(vo));

        List<SysRoleVO> result = sysRoleService.findAllEnabled();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(1);
    }
}
