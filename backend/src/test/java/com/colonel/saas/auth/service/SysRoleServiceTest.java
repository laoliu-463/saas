package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.vo.SysRoleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
    @InjectMocks private SysRoleService sysRoleService;

    private SysRole testRole;
    private final UUID roleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testRole = new SysRole();
        testRole.setId(roleId);
        testRole.setRoleCode("TEST_ROLE");
        testRole.setRoleName("测试角色");
        testRole.setDataScope(1);
        testRole.setStatus(1);
        testRole.setRemark("测试备注");
    }

    @Test
    void findPage_returnsPageWithData() {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(roleId);
        vo.setRoleCode("TEST_ROLE");
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

        assertThat(result.getRoleCode()).isEqualTo("TEST_ROLE");
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
        when(sysRoleMapper.findByRoleCode("NEW_ROLE")).thenReturn(Optional.empty());
        when(sysRoleMapper.insert(any())).thenAnswer(invocation -> {
            SysRole role = invocation.getArgument(0);
            role.setId(UUID.randomUUID());
            return 1;
        });

        SysRoleCreateRequest request = new SysRoleCreateRequest(
                "NEW_ROLE", "新角色", 1, 1, "新角色备注");

        SysRoleVO result = sysRoleService.create(request);

        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(sysRoleMapper).insert(captor.capture());
        assertThat(captor.getValue().getRoleCode()).isEqualTo("NEW_ROLE");
        assertThat(captor.getValue().getDataScope()).isEqualTo(1);
        assertThat(result.getRoleName()).isEqualTo("新角色");
    }

    @Test
    void create_throwsWhenRoleCodeExists() {
        when(sysRoleMapper.findByRoleCode("EXISTING")).thenReturn(Optional.of(testRole));

        SysRoleCreateRequest request = new SysRoleCreateRequest(
                "EXISTING", "重复角色", 1, 1, null);

        assertThatThrownBy(() -> sysRoleService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色编码已存在");
    }

    @Test
    void update_successfullyUpdatesRole() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);
        when(sysRoleMapper.findByRoleCode("UPDATED_ROLE")).thenReturn(Optional.empty());

        SysRoleUpdateRequest request = new SysRoleUpdateRequest(
                "UPDATED_ROLE", "更新角色", 2, 1, "更新备注");

        SysRoleVO result = sysRoleService.update(roleId, request);

        verify(sysRoleMapper).updateById(any());
        assertThat(result.getRoleName()).isEqualTo("更新角色");
        assertThat(result.getDataScope()).isEqualTo(2);
    }

    @Test
    void update_throwsWhenRoleNotFound() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(null);

        SysRoleUpdateRequest request = new SysRoleUpdateRequest(
                "CODE", "名称", 1, 1, null);

        assertThatThrownBy(() -> sysRoleService.update(roleId, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void delete_successfullyDeletesRole() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(testRole);

        sysRoleService.delete(roleId);

        verify(sysRoleMapper).softDeleteById(roleId);
    }

    @Test
    void delete_throwsWhenRoleNotFound() {
        when(sysRoleMapper.selectById(roleId)).thenReturn(null);

        assertThatThrownBy(() -> sysRoleService.delete(roleId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("角色不存在");
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
