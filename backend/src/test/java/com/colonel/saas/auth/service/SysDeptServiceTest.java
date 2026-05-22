package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.SysDeptVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    SysDeptMapper sysDeptMapper;
    @Mock
    OperationLogService operationLogService;

    private SysDeptService service;
    private UUID userId;
    private UUID rootId;
    private UUID childId;

    @BeforeEach
    void setUp() {
        service = new SysDeptService(sysDeptMapper, operationLogService);
        userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        rootId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        childId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    }

    @Test
    void findTreeBuildsParentChildStructureAndTreatsMissingParentAsRoot() {
        SysDept root = dept(rootId, null, "ROOT", "Root");
        SysDept child = dept(childId, rootId, "CHILD", "Child");
        SysDept orphan = dept(UUID.randomUUID(), UUID.randomUUID(), "ORPHAN", "Orphan");
        when(sysDeptMapper.findAllActive()).thenReturn(List.of(root, child, orphan));

        List<SysDeptVO> tree = service.findTree();

        assertThat(tree).extracting(SysDeptVO::getDeptCode).containsExactly("ROOT", "ORPHAN");
        assertThat(tree.get(0).getChildren()).singleElement()
                .extracting(SysDeptVO::getDeptCode)
                .isEqualTo("CHILD");
    }

    @Test
    void findAllAndGetByIdMapDepartments() {
        SysDept root = dept(rootId, null, "ROOT", "Root");
        when(sysDeptMapper.findAllActive()).thenReturn(List.of(root));
        when(sysDeptMapper.selectById(rootId)).thenReturn(root);

        assertThat(service.findAll()).singleElement().satisfies(vo -> {
            assertThat(vo.getId()).isEqualTo(rootId);
            assertThat(vo.getDeptCode()).isEqualTo("ROOT");
            assertThat(vo.getDeptName()).isEqualTo("Root");
        });
        assertThat(service.getById(rootId).getDeptCode()).isEqualTo("ROOT");
    }

    @Test
    void createTrimsFieldsValidatesParentAndRecordsLog() {
        when(sysDeptMapper.findByDeptCode("NEW")).thenReturn(Optional.empty());
        when(sysDeptMapper.selectById(rootId)).thenReturn(dept(rootId, null, "ROOT", "Root"));
        SysDeptCreateRequest request = new SysDeptCreateRequest(
                rootId,
                " NEW ",
                " New Dept ",
                "Leader",
                "13800000000",
                "mail@example.com",
                null,
                null,
                "remark"
        );

        SysDeptVO created = service.create(request, userId);

        assertThat(created.getParentId()).isEqualTo(rootId);
        assertThat(created.getDeptCode()).isEqualTo("NEW");
        assertThat(created.getDeptName()).isEqualTo("New Dept");
        assertThat(created.getSortOrder()).isZero();
        assertThat(created.getStatus()).isEqualTo(1);
        verify(sysDeptMapper).insert(any(SysDept.class));
        verify(operationLogService).recordSystemAction(
                userId,
                "部门管理",
                "新建部门",
                "POST",
                "SysDept",
                null,
                "NEW",
                "新建部门: New Dept"
        );
    }

    @Test
    void updateAllowsSameCodeForSelfAndRejectsSelfParent() {
        SysDept existing = dept(childId, rootId, "CHILD", "Child");
        when(sysDeptMapper.selectById(childId)).thenReturn(existing);
        when(sysDeptMapper.findByDeptCode("CHILD")).thenReturn(Optional.of(existing));
        SysDeptUpdateRequest selfParent = new SysDeptUpdateRequest(
                childId,
                "CHILD",
                "Child",
                null,
                null,
                null,
                5,
                0,
                null
        );

        assertThatThrownBy(() -> service.update(childId, selfParent, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上级部门不能选择自己");

        SysDeptUpdateRequest update = new SysDeptUpdateRequest(
                rootId,
                "CHILD",
                " Child Updated ",
                "Leader",
                "138",
                "a@example.com",
                5,
                0,
                "remark"
        );
        when(sysDeptMapper.selectById(rootId)).thenReturn(dept(rootId, null, "ROOT", "Root"));

        SysDeptVO updated = service.update(childId, update, userId);

        assertThat(updated.getParentId()).isEqualTo(rootId);
        assertThat(updated.getDeptName()).isEqualTo("Child Updated");
        assertThat(updated.getSortOrder()).isEqualTo(5);
        assertThat(updated.getStatus()).isZero();
        verify(sysDeptMapper).updateById(existing);
        verify(operationLogService).recordSystemAction(
                userId,
                "部门管理",
                "更新部门",
                "PUT",
                "SysDept",
                childId.toString(),
                "CHILD",
                "更新部门: Child Updated"
        );
    }

    @Test
    void createAndUpdateRejectDuplicateOrBlankCodesAndMissingParents() {
        SysDept existing = dept(rootId, null, "ROOT", "Root");
        when(sysDeptMapper.findByDeptCode("ROOT")).thenReturn(Optional.of(existing));
        SysDeptCreateRequest duplicate = new SysDeptCreateRequest(null, " ROOT ", "Duplicate", null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(duplicate, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门编码已存在");

        SysDeptCreateRequest blank = new SysDeptCreateRequest(null, " ", "Blank", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(blank, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门编码不能为空");

        UUID missingParent = UUID.randomUUID();
        when(sysDeptMapper.findByDeptCode("NEW")).thenReturn(Optional.empty());
        when(sysDeptMapper.selectById(missingParent)).thenReturn(null);
        SysDeptCreateRequest missing = new SysDeptCreateRequest(missingParent, "NEW", "New", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(missing, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门不存在");
    }

    @Test
    void deleteRejectsMissingOrAlreadyDeletedRowsAndLogsSuccessfulDeletion() {
        UUID missingId = UUID.randomUUID();
        when(sysDeptMapper.selectById(missingId)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(missingId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门不存在");

        SysDept deleted = dept(UUID.randomUUID(), null, "DEL", "Deleted");
        deleted.setDeleted(1);
        when(sysDeptMapper.selectById(deleted.getId())).thenReturn(deleted);
        assertThatThrownBy(() -> service.delete(deleted.getId(), userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门不存在");

        SysDept active = dept(rootId, null, "ROOT", "Root");
        when(sysDeptMapper.selectById(rootId)).thenReturn(active);
        when(sysDeptMapper.softDeleteById(rootId)).thenReturn(0);
        assertThatThrownBy(() -> service.delete(rootId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门不存在或已删除");

        when(sysDeptMapper.softDeleteById(rootId)).thenReturn(1);
        service.delete(rootId, userId);

        verify(operationLogService).recordSystemAction(
                userId,
                "部门管理",
                "删除部门",
                "DELETE",
                "SysDept",
                rootId.toString(),
                "ROOT",
                "删除部门: Root"
        );
        verify(sysDeptMapper, never()).softDeleteById(missingId);
    }

    private SysDept dept(UUID id, UUID parentId, String code, String name) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setDeptCode(code);
        dept.setDeptName(name);
        dept.setLeader("Leader");
        dept.setPhone("13800000000");
        dept.setEmail("mail@example.com");
        dept.setSortOrder(1);
        dept.setStatus(1);
        dept.setRemark("remark");
        dept.setDeleted(0);
        return dept;
    }
}
