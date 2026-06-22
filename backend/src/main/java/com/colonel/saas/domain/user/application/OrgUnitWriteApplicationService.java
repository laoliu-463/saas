package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.OrgValidationPolicy;
import com.colonel.saas.domain.user.port.OrgDepartmentRepository;
import com.colonel.saas.domain.user.port.OrgDepartmentRepository.DepartmentRecord;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.SysDeptVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * 组织单元写应用服务。
 */
@Service
public class OrgUnitWriteApplicationService {

    private final OrgDepartmentRepository orgDepartmentRepository;
    private final OrgValidationPolicy orgValidationPolicy;
    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;
    private final OperationLogService operationLogService;

    public OrgUnitWriteApplicationService(
            OrgDepartmentRepository orgDepartmentRepository,
            OrgValidationPolicy orgValidationPolicy,
            CurrentUserPermissionPolicy currentUserPermissionPolicy,
            OperationLogService operationLogService) {
        this.orgDepartmentRepository = orgDepartmentRepository;
        this.orgValidationPolicy = orgValidationPolicy;
        this.currentUserPermissionPolicy = currentUserPermissionPolicy;
        this.operationLogService = operationLogService;
    }

    @Transactional(rollbackFor = Exception.class)
    public SysDeptVO create(SysDeptCreateRequest request, UUID currentUserId) {
        ensureDeptCodeUnique(request.deptCode(), null);
        validateParent(request.parentId(), null);
        String deptType = resolveDeptType(request.deptType());
        DepartmentRecord dept = new DepartmentRecord(
                UUID.randomUUID(),
                request.parentId(),
                request.deptCode().trim(),
                request.deptName().trim(),
                deptType,
                request.leaderUserId(),
                resolveLeaderName(request.leaderUserId(), deptType, request.leader()),
                request.phone(),
                request.email(),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.status() == null ? 1 : request.status(),
                request.remark(),
                0);
        orgDepartmentRepository.insert(dept);
        recordLog(currentUserId, "新建部门", "POST", dept);
        return toVO(dept);
    }

    @Transactional(rollbackFor = Exception.class)
    public SysDeptVO update(UUID id, SysDeptUpdateRequest request, UUID currentUserId, Collection<?> roleCodes) {
        DepartmentRecord dept = requireDept(id);
        assertCanModify(dept, currentUserId, roleCodes);
        ensureDeptCodeUnique(request.deptCode(), id);
        validateParent(request.parentId(), id);
        String deptType = resolveDeptType(request.deptType());
        DepartmentRecord updated = new DepartmentRecord(
                dept.id(),
                request.parentId(),
                request.deptCode().trim(),
                request.deptName().trim(),
                deptType,
                request.leaderUserId(),
                resolveLeaderName(request.leaderUserId(), deptType, request.leader()),
                request.phone(),
                request.email(),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.status() == null ? 1 : request.status(),
                request.remark(),
                dept.deleted());
        orgDepartmentRepository.update(updated);
        recordLog(currentUserId, "更新部门", "PUT", updated);
        return toVO(updated);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID currentUserId, Collection<?> roleCodes) {
        DepartmentRecord dept = requireDept(id);
        assertCanModify(dept, currentUserId, roleCodes);
        orgValidationPolicy.assertCanDeleteDept(id);
        if (orgDepartmentRepository.softDeleteById(id) <= 0) {
            throw BusinessException.notFound("部门不存在或已删除");
        }
        recordLog(currentUserId, "删除部门", "DELETE", dept);
    }

    private void ensureDeptCodeUnique(String deptCode, UUID excludeId) {
        if (!StringUtils.hasText(deptCode)) {
            throw BusinessException.param("部门编码不能为空");
        }
        orgDepartmentRepository.findByDeptCode(deptCode.trim()).ifPresent(existing -> {
            if (excludeId == null || !Objects.equals(existing.id(), excludeId)) {
                throw BusinessException.duplicate("部门编码已存在: " + deptCode);
            }
        });
    }

    private void validateParent(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return;
        }
        if (Objects.equals(parentId, selfId)) {
            throw BusinessException.param("上级部门不能选择自己");
        }
        requireDept(parentId);
    }

    private DepartmentRecord requireDept(UUID id) {
        DepartmentRecord dept = orgDepartmentRepository.findById(id).orElse(null);
        if (dept == null || dept.isDeleted()) {
            throw BusinessException.notFound("部门不存在");
        }
        return dept;
    }

    private void assertCanModify(DepartmentRecord dept, UUID currentUserId, Collection<?> roleCodes) {
        if (currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            return;
        }
        if (currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.CHANNEL_LEADER)
                && Objects.equals(dept.leaderUserId(), currentUserId)) {
            return;
        }
        throw new ForbiddenException("无权修改该部门");
    }

    private String resolveLeaderName(UUID leaderUserId, String deptType, String fallbackLeader) {
        if (leaderUserId == null) {
            return fallbackLeader;
        }
        return orgValidationPolicy.validateGroupLeader(leaderUserId, deptType);
    }

    private String resolveDeptType(String deptType) {
        String normalized = DeptType.normalize(deptType);
        if (!DeptType.isAllowed(normalized)) {
            throw BusinessException.param("组织类型非法: " + deptType);
        }
        return normalized;
    }

    private SysDeptVO toVO(DepartmentRecord dept) {
        SysDeptVO vo = new SysDeptVO();
        vo.setId(dept.id());
        vo.setParentId(dept.parentId());
        vo.setDeptCode(dept.deptCode());
        vo.setDeptName(dept.deptName());
        vo.setDeptType(dept.deptType());
        vo.setLeaderUserId(dept.leaderUserId());
        vo.setLeader(dept.leader());
        vo.setPhone(dept.phone());
        vo.setEmail(dept.email());
        vo.setSortOrder(dept.sortOrder());
        vo.setStatus(dept.status());
        vo.setRemark(dept.remark());
        return vo;
    }

    private void recordLog(UUID currentUserId, String action, String method, DepartmentRecord dept) {
        operationLogService.recordSystemAction(
                currentUserId,
                "部门管理",
                action,
                method,
                "SysDept",
                dept.id() == null ? null : dept.id().toString(),
                dept.deptCode(),
                action + ": " + dept.deptName());
    }
}
