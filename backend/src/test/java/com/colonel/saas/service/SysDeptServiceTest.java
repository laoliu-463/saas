package com.colonel.saas.service;

import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.SysDeptCodes;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysDeptServiceTest {

    @Mock
    private SysDeptMapper sysDeptMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private OperationLogService operationLogService;

    private SysDeptService sysDeptService;

    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sysDeptService = new SysDeptService(sysDeptMapper, sysUserMapper, operationLogService);
    }

    @Test
    void create_shouldPersistNormalizedDeptCodeAndType() {
        when(sysDeptMapper.findByDeptCode("BIZ_EAST")).thenReturn(Optional.empty());

        SysDeptCreateRequest request = new SysDeptCreateRequest(
                null,
                "biz_east",
                "招商一组",
                null,
                null,
                null,
                15,
                1,
                null,
                "recruiter",
                null);

        var vo = sysDeptService.create(request, adminId);

        assertThat(vo.getDeptCode()).isEqualTo("BIZ_EAST");
        assertThat(vo.getDeptType()).isEqualTo("recruiter");

        ArgumentCaptor<SysDept> captor = ArgumentCaptor.forClass(SysDept.class);
        verify(sysDeptMapper).insert(captor.capture());
        assertThat(captor.getValue().getDeptCode()).isEqualTo("BIZ_EAST");
    }

    @Test
    void delete_shouldRejectSeedDept() {
        UUID seedId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SysDept seed = new SysDept();
        seed.setId(seedId);
        seed.setDeptCode(SysDeptCodes.BIZ);
        seed.setDeleted(0);

        when(sysDeptMapper.selectById(seedId)).thenReturn(seed);

        assertThatThrownBy(() -> sysDeptService.delete(seedId, adminId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统内置部门");

        verify(sysDeptMapper, never()).softDeleteById(any());
    }

    @Test
    void delete_shouldRejectWhenUsersAssigned() {
        UUID deptId = UUID.randomUUID();
        SysDept dept = new SysDept();
        dept.setId(deptId);
        dept.setDeptCode("CUSTOM");
        dept.setDeleted(0);

        when(sysDeptMapper.selectById(deptId)).thenReturn(dept);
        when(sysDeptMapper.countUsersByDeptId(deptId)).thenReturn(2L);

        assertThatThrownBy(() -> sysDeptService.delete(deptId, adminId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍有用户");
    }

    @Test
    void listTree_shouldBuildParentChildStructure() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        SysDept root = new SysDept();
        root.setId(rootId);
        root.setDeptCode("BIZ");
        root.setDeptName("招商组");
        root.setDeptType("recruiter");
        root.setSortOrder(10);
        root.setStatus(1);

        SysDept child = new SysDept();
        child.setId(childId);
        child.setParentId(rootId);
        child.setDeptCode("BIZ_EAST");
        child.setDeptName("招商一组");
        child.setDeptType("recruiter");
        child.setSortOrder(20);
        child.setStatus(1);

        when(sysDeptMapper.findAllNonDeleted()).thenReturn(List.of(root, child));

        var tree = sysDeptService.listTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getDeptCode()).isEqualTo("BIZ");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getDeptCode()).isEqualTo("BIZ_EAST");
    }

    @Test
    void update_shouldRejectInvalidDeptType() {
        UUID deptId = UUID.randomUUID();
        SysDept dept = new SysDept();
        dept.setId(deptId);
        dept.setDeptCode("CUSTOM");
        dept.setDeleted(0);

        when(sysDeptMapper.selectById(deptId)).thenReturn(dept);

        SysDeptUpdateRequest request = new SysDeptUpdateRequest(
                null,
                "CUSTOM",
                "自定义组",
                null,
                null,
                null,
                0,
                1,
                null,
                "invalid",
                null);

        assertThatThrownBy(() -> sysDeptService.update(deptId, request, adminId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的部门类型");
    }
}
