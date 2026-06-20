package com.colonel.saas.service;

import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.constant.SysDeptCodes;
import com.colonel.saas.domain.user.infrastructure.SysOrgDepartmentRepositoryAdapter;
import com.colonel.saas.domain.user.infrastructure.SysOrgLeaderDisplayLookupAdapter;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.vo.SysDeptVO;
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
        // DDD-USER-MIGRATION-006: SysDeptService 已改造为 Legacy 委派壳，
        // 通过构造注入 SysDeptApplicationService（DDD 入口）。
        com.colonel.saas.domain.user.application.SysDeptApplicationService applicationService =
                new com.colonel.saas.domain.user.application.SysDeptApplicationService(
                        new SysOrgDepartmentRepositoryAdapter(sysDeptMapper),
                        new SysOrgLeaderDisplayLookupAdapter(sysUserMapper),
                        operationLogService);
        sysDeptService = new SysDeptService(applicationService);
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
                DeptType.RECRUITER_GROUP,
                null);

        var vo = sysDeptService.create(request, adminId);

        assertThat(vo.getDeptCode()).isEqualTo("BIZ_EAST");
        assertThat(vo.getDeptType()).isEqualTo(DeptType.RECRUITER_GROUP);

        ArgumentCaptor<SysDept> captor = ArgumentCaptor.forClass(SysDept.class);
        verify(sysDeptMapper).insert(captor.capture());
        assertThat(captor.getValue().getDeptCode()).isEqualTo("BIZ_EAST");
    }

    @Test
    void create_withExistingLeaderWithoutDisplayName_shouldKeepNullLeader() {
        UUID leaderUserId = UUID.randomUUID();
        SysUser leader = new SysUser();
        leader.setDeleted(0);

        when(sysDeptMapper.findByDeptCode("BIZ_EMPTY_LEADER")).thenReturn(Optional.empty());
        when(sysUserMapper.selectById(leaderUserId)).thenReturn(leader);

        SysDeptCreateRequest request = new SysDeptCreateRequest(
                null,
                "biz_empty_leader",
                "无展示名负责人组",
                "备用负责人",
                null,
                null,
                15,
                1,
                null,
                DeptType.RECRUITER_GROUP,
                leaderUserId);

        sysDeptService.create(request, adminId);

        ArgumentCaptor<SysDept> captor = ArgumentCaptor.forClass(SysDept.class);
        verify(sysDeptMapper).insert(captor.capture());
        assertThat(captor.getValue().getLeader()).isNull();
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
        root.setDeptType(DeptType.RECRUITER_GROUP);
        root.setSortOrder(10);
        root.setStatus(1);

        SysDept child = new SysDept();
        child.setId(childId);
        child.setParentId(rootId);
        child.setDeptCode("BIZ_EAST");
        child.setDeptName("招商一组");
        child.setDeptType(DeptType.RECRUITER_GROUP);
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

    @Test
    void create_shouldRejectLegacyDeptTypeValues() {
        when(sysDeptMapper.findByDeptCode("BIZ_LEGACY")).thenReturn(Optional.empty());

        SysDeptCreateRequest request = new SysDeptCreateRequest(
                null,
                "BIZ_LEGACY",
                "旧招商组",
                null,
                null,
                null,
                15,
                1,
                null,
                "recruiter",
                null);

        assertThatThrownBy(() -> sysDeptService.create(request, adminId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的部门类型");

        verify(sysDeptMapper, never()).insert(any());
    }

    // ===== 补充测试（DDD-USER-MIGRATION-006）=====

    @Test
    void getById_notFound_shouldThrow() {
        UUID deptId = UUID.randomUUID();
        when(sysDeptMapper.selectById(deptId)).thenReturn(null);

        assertThatThrownBy(() -> sysDeptService.getById(deptId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getById_deletedDept_shouldThrow() {
        UUID deptId = UUID.randomUUID();
        SysDept dept = new SysDept();
        dept.setId(deptId);
        dept.setDeleted(1);
        when(sysDeptMapper.selectById(deptId)).thenReturn(dept);

        assertThatThrownBy(() -> sysDeptService.getById(deptId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getById_validDept_shouldReturnVO() {
        UUID deptId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        SysDept dept = new SysDept();
        dept.setId(deptId);
        dept.setParentId(parentId);
        dept.setDeptName("渠道部");
        dept.setDeptCode("CHANNEL_001");
        dept.setDeptType(DeptType.DEPARTMENT);
        dept.setStatus(1);
        when(sysDeptMapper.selectById(deptId)).thenReturn(dept);

        SysDeptVO vo = sysDeptService.getById(deptId);
        assertThat(vo.getId()).isEqualTo(deptId);
        assertThat(vo.getDeptName()).isEqualTo("渠道部");
    }

    @Test
    void delete_shouldThrowWhenDeptNotFound() {
        UUID deptId = UUID.randomUUID();
        when(sysDeptMapper.selectById(deptId)).thenReturn(null);

        assertThatThrownBy(() -> sysDeptService.delete(deptId, adminId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void delete_shouldThrowWhenAlreadyDeleted() {
        UUID deptId = UUID.randomUUID();
        SysDept dept = new SysDept();
        dept.setId(deptId);
        dept.setDeleted(1);
        when(sysDeptMapper.selectById(deptId)).thenReturn(dept);

        assertThatThrownBy(() -> sysDeptService.delete(deptId, adminId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_shouldThrowWhenDeptNotFound() {
        UUID deptId = UUID.randomUUID();
        when(sysDeptMapper.selectById(deptId)).thenReturn(null);

        SysDeptUpdateRequest request = new SysDeptUpdateRequest(
                null, "CHANNEL_001", "渠道部", null, null, null,
                1, 1, null, "department", null);

        assertThatThrownBy(() -> sysDeptService.update(deptId, request, adminId))
                .isInstanceOf(BusinessException.class);
    }
}
