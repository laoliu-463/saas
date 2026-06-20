package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.constant.SysDeptCodes;
import com.colonel.saas.domain.user.port.OrgDepartmentRepository;
import com.colonel.saas.domain.user.port.OrgDepartmentRepository.DepartmentRecord;
import com.colonel.saas.domain.user.port.OrgLeaderDisplayLookup;
import com.colonel.saas.domain.user.port.OrgLeaderDisplayLookup.LeaderDisplay;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.SysDeptVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 部门与组别应用服务（DDD-USER-MIGRATION-006，Issue #15）。
 *
 * <p>用户域 / 组织架构的 DDD 入口。当前为过渡实现 —— 旧 {@code SysDeptService}
 * 只保留 public 签名兼容，组织管理用例由本应用服务编排。
 * 后续可以拆分为独立 Policy（DeptCodePolicy / DeptHierarchyPolicy / DeptValidationPolicy）。</p>
 *
 * <p><b>行为 1:1 等价</b>于 SysDeptService 旧实现（由 SysDeptServiceTest 验证）。</p>
 *
 * <p><b>7 个 public 方法</b>：
 * <ul>
 *   <li>listActive / listByDeptType / listTree —— 查询</li>
 *   <li>getById —— 单个查询</li>
 *   <li>create / update / delete —— CRUD（事务性）</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 组织架构</p>
 */
@Service
public class SysDeptApplicationService {

    private final OrgDepartmentRepository orgDepartmentRepository;
    private final OrgLeaderDisplayLookup orgLeaderDisplayLookup;
    private final OperationLogService operationLogService;

    public SysDeptApplicationService(
            OrgDepartmentRepository orgDepartmentRepository,
            OrgLeaderDisplayLookup orgLeaderDisplayLookup,
            OperationLogService operationLogService) {
        this.orgDepartmentRepository = orgDepartmentRepository;
        this.orgLeaderDisplayLookup = orgLeaderDisplayLookup;
        this.operationLogService = operationLogService;
    }

    // ===== 查询 =====

    public List<DepartmentRecord> listActive() {
        return orgDepartmentRepository.listActive();
    }

    public List<DepartmentRecord> listByDeptType(String deptType) {
        String normalized = DeptType.normalize(deptType);
        if (!DeptType.isAllowed(normalized)) {
            throw new BusinessException("无效的部门类型: " + deptType);
        }
        return orgDepartmentRepository.listByDeptType(normalized);
    }

    public List<SysDeptVO> listTree() {
        List<DepartmentRecord> all = orgDepartmentRepository.listNonDeleted();
        return buildTree(all);
    }

    public SysDeptVO getById(UUID id) {
        return toVO(requireDept(id));
    }

    // ===== CRUD（事务性）=====

    @Transactional(rollbackFor = Exception.class)
    public SysDeptVO create(SysDeptCreateRequest request, UUID currentUserId) {
        String deptCode = normalizeDeptCode(request.deptCode());
        ensureDeptCodeUnique(deptCode, null);
        String deptType = requireValidDeptType(request.deptType());
        validateParent(request.parentId(), null);

        DepartmentRecord dept = new DepartmentRecord(
                UUID.randomUUID(),
                request.parentId(),
                deptCode,
                request.deptName().trim(),
                deptType,
                request.leaderUserId(),
                resolveLeader(request.leader(), request.leaderUserId()),
                trimToNull(request.phone()),
                trimToNull(request.email()),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.status() == null ? 1 : request.status(),
                trimToNull(request.remark()),
                0);

        orgDepartmentRepository.insert(dept);
        recordLog(currentUserId, "新建部门", "POST", dept);
        return toVO(dept);
    }

    @Transactional(rollbackFor = Exception.class)
    public SysDeptVO update(UUID id, SysDeptUpdateRequest request, UUID currentUserId) {
        DepartmentRecord dept = requireDept(id);
        String deptType = requireValidDeptType(request.deptType());
        validateParent(request.parentId(), id);

        Integer sortOrder = request.sortOrder() != null ? request.sortOrder() : dept.sortOrder();
        Integer status = request.status() != null ? request.status() : dept.status();
        if (request.status() != null) {
            if (request.status() == 0 && orgDepartmentRepository.countUsersByDeptId(id) > 0) {
                throw new BusinessException("部门下仍有用户，不能禁用");
            }
        }

        DepartmentRecord updated = new DepartmentRecord(
                dept.id(),
                request.parentId(),
                dept.deptCode(),
                request.deptName().trim(),
                deptType,
                request.leaderUserId(),
                resolveLeader(request.leader(), request.leaderUserId()),
                trimToNull(request.phone()),
                trimToNull(request.email()),
                sortOrder,
                status,
                trimToNull(request.remark()),
                dept.deleted());

        orgDepartmentRepository.update(updated);
        recordLog(currentUserId, "更新部门", "PUT", updated);
        return toVO(updated);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID currentUserId) {
        DepartmentRecord dept = requireDept(id);
        if (SysDeptCodes.isSeedCode(dept.deptCode())) {
            throw new BusinessException("系统内置部门不允许删除");
        }
        if (orgDepartmentRepository.countUsersByDeptId(id) > 0) {
            throw new BusinessException("部门下仍有用户，不能删除");
        }
        if (orgDepartmentRepository.countChildrenByParentId(id) > 0) {
            throw new BusinessException("存在子部门，不能删除");
        }
        orgDepartmentRepository.softDeleteById(id);
        recordLog(currentUserId, "删除部门", "DELETE", dept);
    }

    // ===== 私有辅助方法（与 SysDeptService 一致）=====

    private DepartmentRecord requireDept(UUID id) {
        if (id == null) {
            throw new BusinessException("部门不存在");
        }
        DepartmentRecord dept = orgDepartmentRepository.findById(id).orElse(null);
        if (dept == null || dept.isDeleted()) {
            throw new BusinessException("部门不存在");
        }
        return dept;
    }

    private void ensureDeptCodeUnique(String deptCode, UUID excludeId) {
        orgDepartmentRepository.findByDeptCode(deptCode).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.id())) {
                throw new BusinessException("部门编码已存在: " + deptCode);
            }
        });
    }

    private String normalizeDeptCode(String deptCode) {
        if (!StringUtils.hasText(deptCode)) {
            throw new BusinessException("部门编码不能为空");
        }
        return deptCode.trim().toUpperCase();
    }

    private String requireValidDeptType(String deptType) {
        String normalized = DeptType.normalize(deptType);
        if (!DeptType.isAllowed(normalized)) {
            throw new BusinessException("无效的部门类型，允许值: department, recruiter_group, channel_group, ops_group");
        }
        return normalized;
    }

    private void validateParent(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BusinessException("父部门不能是自身");
        }
        DepartmentRecord parent = requireDept(parentId);
        if (selfId != null && isDescendant(selfId, parent.id())) {
            throw new BusinessException("父部门不能是当前部门的子级");
        }
    }

    private boolean isDescendant(UUID ancestorId, UUID nodeId) {
        List<DepartmentRecord> all = orgDepartmentRepository.listNonDeleted();
        Map<UUID, UUID> parentById = new HashMap<>();
        for (DepartmentRecord dept : all) {
            if (dept.id() != null) {
                parentById.put(dept.id(), dept.parentId());
            }
        }
        UUID current = nodeId;
        while (current != null) {
            if (ancestorId.equals(current)) {
                return true;
            }
            current = parentById.get(current);
        }
        return false;
    }

    private String resolveLeader(String leader, UUID leaderUserId) {
        if (leaderUserId != null) {
            LeaderDisplay leaderDisplay = orgLeaderDisplayLookup.findDisplay(leaderUserId)
                    .orElseThrow(() -> new BusinessException("负责人用户不存在"));
            return leaderDisplay.displayName();
        }
        return trimToNull(leader);
    }

    private List<SysDeptVO> buildTree(List<DepartmentRecord> all) {
        Map<UUID, SysDeptVO> voById = new HashMap<>();
        for (DepartmentRecord dept : all) {
            voById.put(dept.id(), toVO(dept));
        }
        List<SysDeptVO> roots = new ArrayList<>();
        for (DepartmentRecord dept : all) {
            SysDeptVO vo = voById.get(dept.id());
            UUID parentId = dept.parentId();
            if (parentId == null || !voById.containsKey(parentId)) {
                roots.add(vo);
            } else {
                voById.get(parentId).getChildren().add(vo);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<SysDeptVO> nodes) {
        nodes.sort(Comparator
                .comparing(SysDeptVO::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SysDeptVO::getDeptName, Comparator.nullsLast(String::compareTo)));
        for (SysDeptVO node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortTree(node.getChildren());
            }
        }
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
                action + ": " + dept.deptCode() + " / " + dept.deptName());
    }
}
