package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.DeptStatsVO;
import com.colonel.saas.vo.SysDeptVO;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class SysDeptService {

    private final SysDeptMapper sysDeptMapper;
    private final OperationLogService operationLogService;
    private final OrgStructureService orgStructureService;
    private final SysUserService sysUserService;

    public SysDeptService(
            SysDeptMapper sysDeptMapper,
            OperationLogService operationLogService,
            OrgStructureService orgStructureService,
            SysUserService sysUserService) {
        this.sysDeptMapper = sysDeptMapper;
        this.operationLogService = operationLogService;
        this.orgStructureService = orgStructureService;
        this.sysUserService = sysUserService;
    }

    public List<SysDeptVO> findTree() {
        List<SysDept> depts = sysDeptMapper.findAllActive();
        Map<UUID, SysDeptVO> index = new LinkedHashMap<>();
        for (SysDept dept : depts) {
            index.put(dept.getId(), toVO(dept));
        }
        List<SysDeptVO> roots = new ArrayList<>();
        for (SysDeptVO node : index.values()) {
            if (node.getParentId() != null && index.containsKey(node.getParentId())) {
                index.get(node.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    public List<SysDeptVO> findAll() {
        return sysDeptMapper.findAllActive().stream().map(this::toVO).toList();
    }

    public List<SysDeptVO> findGroupsByParent(UUID parentId, String deptType) {
        requireDept(parentId);
        return sysDeptMapper.findByParentId(parentId).stream()
                .filter(dept -> !StringUtils.hasText(deptType)
                        || DeptType.normalize(deptType).equals(DeptType.normalize(dept.getDeptType())))
                .filter(dept -> DeptType.isGroup(dept.getDeptType()))
                .map(this::toVO)
                .toList();
    }

    public DeptStatsVO getStats(UUID deptId) {
        requireDept(deptId);
        DeptStatsVO stats = new DeptStatsVO();
        stats.setDeptId(deptId);
        stats.setMemberCount(sysDeptMapper.countMembersUnderDept(deptId));
        stats.setRecruiterGroupCount(sysDeptMapper.countChildGroupsByType(deptId, DeptType.RECRUITER_GROUP));
        stats.setChannelGroupCount(sysDeptMapper.countChildGroupsByType(deptId, DeptType.CHANNEL_GROUP));
        return stats;
    }

    public IPage<SysUserVO> findMembers(UUID deptId, DeptMemberPageRequest request) {
        requireDept(deptId);
        return sysUserService.findDeptMembers(deptId, request);
    }

    public SysDeptVO getById(UUID id) {
        return toVO(requireDept(id));
    }

    public SysDeptVO create(SysDeptCreateRequest request, UUID currentUserId) {
        ensureDeptCodeUnique(request.deptCode(), null);
        validateParent(request.parentId(), null);
        SysDept dept = new SysDept();
        dept.setParentId(request.parentId());
        dept.setDeptCode(request.deptCode().trim());
        dept.setDeptName(request.deptName().trim());
        dept.setDeptType(resolveDeptType(request.deptType()));
        dept.setLeaderUserId(request.leaderUserId());
        if (request.leaderUserId() != null) {
            orgStructureService.validateGroupLeader(request.leaderUserId(), dept.getDeptType());
        }
        dept.setLeader(request.leader());
        dept.setPhone(request.phone());
        dept.setEmail(request.email());
        dept.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        dept.setStatus(request.status() == null ? 1 : request.status());
        dept.setRemark(request.remark());
        sysDeptMapper.insert(dept);
        operationLogService.recordSystemAction(
                currentUserId,
                "部门管理",
                "新建部门",
                "POST",
                "SysDept",
                dept.getId() == null ? null : dept.getId().toString(),
                dept.getDeptCode(),
                "新建部门: " + dept.getDeptName()
        );
        return toVO(dept);
    }

    public SysDeptVO update(UUID id, SysDeptUpdateRequest request, UUID currentUserId) {
        SysDept dept = requireDept(id);
        ensureDeptCodeUnique(request.deptCode(), id);
        validateParent(request.parentId(), id);
        dept.setParentId(request.parentId());
        dept.setDeptCode(request.deptCode().trim());
        dept.setDeptName(request.deptName().trim());
        dept.setDeptType(resolveDeptType(request.deptType()));
        dept.setLeaderUserId(request.leaderUserId());
        if (request.leaderUserId() != null) {
            orgStructureService.validateGroupLeader(request.leaderUserId(), dept.getDeptType());
        }
        dept.setLeader(request.leader());
        dept.setPhone(request.phone());
        dept.setEmail(request.email());
        dept.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        dept.setStatus(request.status() == null ? 1 : request.status());
        dept.setRemark(request.remark());
        sysDeptMapper.updateById(dept);
        operationLogService.recordSystemAction(
                currentUserId,
                "部门管理",
                "更新部门",
                "PUT",
                "SysDept",
                id.toString(),
                dept.getDeptCode(),
                "更新部门: " + dept.getDeptName()
        );
        return toVO(dept);
    }

    public void delete(UUID id, UUID currentUserId) {
        SysDept dept = requireDept(id);
        orgStructureService.assertCanDeleteDept(id);
        if (sysDeptMapper.softDeleteById(id) <= 0) {
            throw BusinessException.notFound("部门不存在或已删除");
        }
        operationLogService.recordSystemAction(
                currentUserId,
                "部门管理",
                "删除部门",
                "DELETE",
                "SysDept",
                id.toString(),
                dept.getDeptCode(),
                "删除部门: " + dept.getDeptName()
        );
    }

    private void ensureDeptCodeUnique(String deptCode, UUID excludeId) {
        if (!StringUtils.hasText(deptCode)) {
            throw BusinessException.param("部门编码不能为空");
        }
        sysDeptMapper.findByDeptCode(deptCode.trim()).ifPresent(existing -> {
            if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
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

    private SysDept requireDept(UUID id) {
        SysDept dept = sysDeptMapper.selectById(id);
        if (dept == null || Objects.equals(dept.getDeleted(), 1)) {
            throw BusinessException.notFound("部门不存在");
        }
        return dept;
    }

    private SysDeptVO toVO(SysDept dept) {
        SysDeptVO vo = new SysDeptVO();
        vo.setId(dept.getId());
        vo.setParentId(dept.getParentId());
        vo.setDeptCode(dept.getDeptCode());
        vo.setDeptName(dept.getDeptName());
        vo.setDeptType(dept.getDeptType());
        vo.setLeaderUserId(dept.getLeaderUserId());
        vo.setLeader(dept.getLeader());
        vo.setPhone(dept.getPhone());
        vo.setEmail(dept.getEmail());
        vo.setSortOrder(dept.getSortOrder());
        vo.setStatus(dept.getStatus());
        vo.setRemark(dept.getRemark());
        return vo;
    }

    private String resolveDeptType(String deptType) {
        String normalized = DeptType.normalize(deptType);
        if (!DeptType.isAllowed(normalized)) {
            throw BusinessException.param("组织类型非法: " + deptType);
        }
        return normalized;
    }
}
