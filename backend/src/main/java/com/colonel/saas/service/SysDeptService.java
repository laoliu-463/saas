package com.colonel.saas.service;

import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.constant.SysDeptCodes;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.mapper.SysUserMapper;
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

@Service("legacySysDeptService")
public class SysDeptService {

    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;
    private final OperationLogService operationLogService;

    public SysDeptService(
            SysDeptMapper sysDeptMapper,
            SysUserMapper sysUserMapper,
            OperationLogService operationLogService) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysUserMapper = sysUserMapper;
        this.operationLogService = operationLogService;
    }

    public List<SysDept> listActive() {
        return sysDeptMapper.findAllActive();
    }

    public List<SysDept> listByDeptType(String deptType) {
        String normalized = DeptType.normalize(deptType);
        if (!DeptType.isAllowed(normalized)) {
            throw new BusinessException("无效的部门类型: " + deptType);
        }
        return sysDeptMapper.findByDeptType(normalized);
    }

    public List<SysDeptVO> listTree() {
        List<SysDept> all = sysDeptMapper.findAllNonDeleted();
        return buildTree(all);
    }

    public SysDeptVO getById(UUID id) {
        return toVO(requireDept(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public SysDeptVO create(SysDeptCreateRequest request, UUID currentUserId) {
        String deptCode = normalizeDeptCode(request.deptCode());
        ensureDeptCodeUnique(deptCode, null);
        String deptType = requireValidDeptType(request.deptType());
        validateParent(request.parentId(), null);

        SysDept dept = new SysDept();
        dept.setId(UUID.randomUUID());
        dept.setParentId(request.parentId());
        dept.setDeptCode(deptCode);
        dept.setDeptName(request.deptName().trim());
        dept.setDeptType(deptType);
        applyLeader(dept, request.leader(), request.leaderUserId());
        dept.setPhone(trimToNull(request.phone()));
        dept.setEmail(trimToNull(request.email()));
        dept.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        dept.setStatus(request.status() == null ? 1 : request.status());
        dept.setRemark(trimToNull(request.remark()));

        sysDeptMapper.insert(dept);
        recordLog(currentUserId, "新建部门", "POST", dept);
        return toVO(dept);
    }

    @Transactional(rollbackFor = Exception.class)
    public SysDeptVO update(UUID id, SysDeptUpdateRequest request, UUID currentUserId) {
        SysDept dept = requireDept(id);
        String deptType = requireValidDeptType(request.deptType());
        validateParent(request.parentId(), id);

        dept.setParentId(request.parentId());
        dept.setDeptName(request.deptName().trim());
        dept.setDeptType(deptType);
        applyLeader(dept, request.leader(), request.leaderUserId());
        dept.setPhone(trimToNull(request.phone()));
        dept.setEmail(trimToNull(request.email()));
        if (request.sortOrder() != null) {
            dept.setSortOrder(request.sortOrder());
        }
        if (request.status() != null) {
            if (request.status() == 0 && sysDeptMapper.countUsersByDeptId(id) > 0) {
                throw new BusinessException("部门下仍有用户，不能禁用");
            }
            dept.setStatus(request.status());
        }
        dept.setRemark(trimToNull(request.remark()));

        sysDeptMapper.updateById(dept);
        recordLog(currentUserId, "更新部门", "PUT", dept);
        return toVO(dept);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID currentUserId) {
        SysDept dept = requireDept(id);
        if (SysDeptCodes.isSeedCode(dept.getDeptCode())) {
            throw new BusinessException("系统内置部门不允许删除");
        }
        if (sysDeptMapper.countUsersByDeptId(id) > 0) {
            throw new BusinessException("部门下仍有用户，不能删除");
        }
        if (sysDeptMapper.countChildrenByParentId(id) > 0) {
            throw new BusinessException("存在子部门，不能删除");
        }
        sysDeptMapper.softDeleteById(id);
        recordLog(currentUserId, "删除部门", "DELETE", dept);
    }

    private SysDept requireDept(UUID id) {
        if (id == null) {
            throw new BusinessException("部门不存在");
        }
        SysDept dept = sysDeptMapper.selectById(id);
        if (dept == null || dept.getDeleted() != null && dept.getDeleted() != 0) {
            throw new BusinessException("部门不存在");
        }
        return dept;
    }

    private void ensureDeptCodeUnique(String deptCode, UUID excludeId) {
        sysDeptMapper.findByDeptCode(deptCode).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
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
        SysDept parent = requireDept(parentId);
        if (selfId != null && isDescendant(selfId, parent.getId())) {
            throw new BusinessException("父部门不能是当前部门的子级");
        }
    }

    private boolean isDescendant(UUID ancestorId, UUID nodeId) {
        List<SysDept> all = sysDeptMapper.findAllNonDeleted();
        Map<UUID, UUID> parentById = new HashMap<>();
        for (SysDept dept : all) {
            if (dept.getId() != null) {
                parentById.put(dept.getId(), dept.getParentId());
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

    private void applyLeader(SysDept dept, String leader, UUID leaderUserId) {
        if (leaderUserId != null) {
            SysUser user = sysUserMapper.selectById(leaderUserId);
            if (user == null || user.getDeleted() != null && user.getDeleted() != 0) {
                throw new BusinessException("负责人用户不存在");
            }
            String display = StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
            dept.setLeader(display);
            return;
        }
        dept.setLeader(trimToNull(leader));
    }

    private List<SysDeptVO> buildTree(List<SysDept> all) {
        Map<UUID, SysDeptVO> voById = new HashMap<>();
        for (SysDept dept : all) {
            voById.put(dept.getId(), toVO(dept));
        }
        List<SysDeptVO> roots = new ArrayList<>();
        for (SysDept dept : all) {
            SysDeptVO vo = voById.get(dept.getId());
            UUID parentId = dept.getParentId();
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

    private SysDeptVO toVO(SysDept dept) {
        SysDeptVO vo = new SysDeptVO();
        vo.setId(dept.getId());
        vo.setParentId(dept.getParentId());
        vo.setDeptCode(dept.getDeptCode());
        vo.setDeptName(dept.getDeptName());
        vo.setDeptType(dept.getDeptType());
        vo.setLeader(dept.getLeader());
        vo.setPhone(dept.getPhone());
        vo.setEmail(dept.getEmail());
        vo.setSortOrder(dept.getSortOrder());
        vo.setStatus(dept.getStatus());
        vo.setRemark(dept.getRemark());
        return vo;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void recordLog(UUID currentUserId, String action, String method, SysDept dept) {
        operationLogService.recordSystemAction(
                currentUserId,
                "部门管理",
                action,
                method,
                "SysDept",
                dept.getId() == null ? null : dept.getId().toString(),
                dept.getDeptCode(),
                action + ": " + dept.getDeptCode() + " / " + dept.getDeptName());
    }
}
