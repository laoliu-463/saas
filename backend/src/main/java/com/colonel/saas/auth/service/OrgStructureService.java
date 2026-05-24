package com.colonel.saas.auth.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 组织归属解析：sys_dept 同时承载部门与业务组，用户仅持久化 {@code sys_user.dept_id} 一个外键。
 */
@Service
public class OrgStructureService {

    private static final Set<String> RECRUITER_LEADER_ROLES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.COLONEL_LEADER,
            RoleCodes.ADMIN);
    private static final Set<String> CHANNEL_LEADER_ROLES = Set.of(
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.ADMIN);
    private static final Set<String> OPS_LEADER_ROLES = Set.of(
            RoleCodes.OPS_STAFF,
            RoleCodes.ADMIN);

    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public OrgStructureService(
            SysDeptMapper sysDeptMapper,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    public record ResolvedAssignment(UUID effectiveDeptId, UUID parentDeptId, UUID groupId) {
    }

    public record SplitAssignment(UUID parentDeptId, UUID groupId, String parentDeptName, String groupName, String groupType) {
    }

    public ResolvedAssignment resolveAssignment(UUID parentDeptId, UUID groupId) {
        if (groupId != null) {
            SysDept group = requireActiveDept(groupId);
            if (!DeptType.isGroup(group.getDeptType())) {
                throw BusinessException.param("所属组别必须为招商组/渠道组/运营组");
            }
            if (parentDeptId != null && !Objects.equals(group.getParentId(), parentDeptId)) {
                throw BusinessException.param("所选组别不属于当前部门");
            }
            UUID resolvedParent = group.getParentId() != null ? group.getParentId() : parentDeptId;
            return new ResolvedAssignment(groupId, resolvedParent, groupId);
        }
        if (parentDeptId != null) {
            SysDept dept = requireActiveDept(parentDeptId);
            if (!DeptType.isDepartment(dept.getDeptType()) && !DeptType.OPS_GROUP.equals(DeptType.normalize(dept.getDeptType()))) {
                throw BusinessException.param("所属部门类型无效");
            }
            return new ResolvedAssignment(parentDeptId, parentDeptId, null);
        }
        return new ResolvedAssignment(null, null, null);
    }

    public SplitAssignment splitAssignment(UUID effectiveDeptId) {
        if (effectiveDeptId == null) {
            return new SplitAssignment(null, null, null, null, null);
        }
        SysDept node = sysDeptMapper.selectById(effectiveDeptId);
        if (node == null || Objects.equals(node.getDeleted(), 1)) {
            return new SplitAssignment(null, null, null, null, null);
        }
        if (DeptType.isGroup(node.getDeptType())) {
            SysDept parent = node.getParentId() == null ? null : sysDeptMapper.selectById(node.getParentId());
            return new SplitAssignment(
                    node.getParentId(),
                    node.getId(),
                    parent == null ? null : parent.getDeptName(),
                    node.getDeptName(),
                    DeptType.normalize(node.getDeptType()));
        }
        return new SplitAssignment(
                node.getId(),
                null,
                node.getDeptName(),
                null,
                DeptType.normalize(node.getDeptType()));
    }

    public void enrichUserList(List<SysUserVO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<UUID, SysDept> deptMap = loadDeptMap();
        Map<UUID, SysRole> roleMap = loadRoleMap();
        for (SysUserVO user : users) {
            applyOrgFields(user, deptMap);
            applyPrimaryRole(user, roleMap);
        }
    }

    public SysUserVO enrichUser(SysUserVO user) {
        if (user == null) {
            return null;
        }
        enrichUserList(List.of(user));
        return user;
    }

    public void validateGroupLeader(UUID leaderUserId, String groupType) {
        if (leaderUserId == null) {
            return;
        }
        SysUser leader = sysUserMapper.selectById(leaderUserId);
        if (leader == null || Objects.equals(leader.getDeleted(), 1)) {
            throw BusinessException.param("组长必须是系统内有效用户");
        }
        Set<String> roleCodes = sysUserRoleMapper.findByUserId(leaderUserId).stream()
                .map(SysUserRole::getRoleId)
                .map(sysRoleMapper::selectById)
                .filter(Objects::nonNull)
                .map(SysRole::getRoleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        String normalizedType = DeptType.normalize(groupType);
        Set<String> allowed = switch (normalizedType) {
            case DeptType.RECRUITER_GROUP -> RECRUITER_LEADER_ROLES;
            case DeptType.CHANNEL_GROUP -> CHANNEL_LEADER_ROLES;
            case DeptType.OPS_GROUP -> OPS_LEADER_ROLES;
            default -> Set.of(RoleCodes.ADMIN);
        };
        if (roleCodes.stream().noneMatch(allowed::contains)) {
            throw BusinessException.param("组长角色与组别类型不匹配");
        }
    }

    public void assertCanDeleteDept(UUID deptId) {
        long directUsers = sysDeptMapper.countUsersByDeptId(deptId);
        if (directUsers > 0) {
            throw BusinessException.stateInvalid("部门或组别下仍有员工，无法删除");
        }
        long childGroups = sysDeptMapper.countChildGroups(deptId);
        if (childGroups > 0) {
            throw BusinessException.stateInvalid("请先删除下级组别");
        }
    }

    private void applyOrgFields(SysUserVO user, Map<UUID, SysDept> deptMap) {
        if (user.getDeptId() == null) {
            return;
        }
        SysDept assigned = deptMap.get(user.getDeptId());
        if (assigned == null) {
            return;
        }
        if (DeptType.isGroup(assigned.getDeptType())) {
            user.setGroupId(assigned.getId());
            user.setGroupName(assigned.getDeptName());
            user.setGroupType(DeptType.normalize(assigned.getDeptType()));
            if (assigned.getParentId() != null) {
                SysDept parent = deptMap.get(assigned.getParentId());
                user.setParentDeptId(assigned.getParentId());
                user.setParentDeptName(parent == null ? null : parent.getDeptName());
            }
        } else {
            user.setParentDeptId(assigned.getId());
            user.setParentDeptName(assigned.getDeptName());
            user.setGroupType(DeptType.normalize(assigned.getDeptType()));
        }
    }

    private void applyPrimaryRole(SysUserVO user, Map<UUID, SysRole> roleMap) {
        List<UUID> roleIds = user.getRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        SysRole role = roleMap.get(roleIds.get(0));
        if (role != null) {
            user.setRoleId(role.getId());
            user.setRoleCode(role.getRoleCode());
            user.setRoleName(role.getRoleName());
        }
    }

    private Map<UUID, SysDept> loadDeptMap() {
        List<SysDept> depts = sysDeptMapper.findAllActive();
        Map<UUID, SysDept> map = new HashMap<>();
        for (SysDept dept : depts) {
            if (dept.getId() != null) {
                map.put(dept.getId(), dept);
            }
        }
        return map;
    }

    private Map<UUID, SysRole> loadRoleMap() {
        List<SysRole> roles = sysRoleMapper.selectList(null);
        if (roles == null) {
            return Collections.emptyMap();
        }
        Map<UUID, SysRole> map = new HashMap<>();
        for (SysRole role : roles) {
            if (role.getId() != null) {
                map.put(role.getId(), role);
            }
        }
        return map;
    }

    private SysDept requireActiveDept(UUID id) {
        SysDept dept = sysDeptMapper.selectById(id);
        if (dept == null || Objects.equals(dept.getDeleted(), 1)) {
            throw BusinessException.notFound("部门或组别不存在");
        }
        return dept;
    }

    public String formatOrgChangeRemark(
            UUID userId,
            UUID oldEffectiveDeptId,
            UUID newEffectiveDeptId,
            UUID operatorId) {
        SplitAssignment oldSplit = splitAssignment(oldEffectiveDeptId);
        SplitAssignment newSplit = splitAssignment(newEffectiveDeptId);
        return String.format(
                "userId=%s operatorId=%s oldDeptId=%s oldGroupId=%s newDeptId=%s newGroupId=%s",
                userId,
                operatorId,
                oldSplit.parentDeptId(),
                oldSplit.groupId(),
                newSplit.parentDeptId(),
                newSplit.groupId());
    }
}
