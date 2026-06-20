package com.colonel.saas.domain.user.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.domain.user.policy.UserAssignmentPolicy;
import com.colonel.saas.domain.user.policy.UserAssignmentPolicy.AssignableScope;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户可分配负责人应用服务。
 *
 * <p>承接旧 {@code SysUserService} 中“可分配负责人查询/校验”入口。
 * 角色与部门范围规则继续由 {@link UserAssignmentPolicy} 统一承载。</p>
 */
@Service
public class UserAssignableApplicationService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final UserAssignmentPolicy assignmentPolicy;

    public UserAssignableApplicationService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            UserAssignmentPolicy assignmentPolicy) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.assignmentPolicy = assignmentPolicy;
    }

    public List<SysUserVO> findAssignableUsers(String keyword, List<String> currentRoleCodes, UUID currentDeptId) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq("status", 1)
                .orderByAsc("real_name")
                .orderByAsc("username")
                .last("limit 20");
        if (keyword != null && !keyword.trim().isEmpty()) {
            String safeKeyword = keyword.trim();
            wrapper.and(query -> query.like("username", safeKeyword).or().like("real_name", safeKeyword));
        }

        List<SysUser> users = sysUserMapper.selectList(wrapper);
        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        AssignableScope scope = assignmentPolicy.resolveAssignableScope(currentRoleCodes, currentDeptId);
        Set<String> allowedRoleCodes = scope.allowedRoleCodes();
        if (allowedRoleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, List<SysUserRole>> relationMap = users.stream()
                .collect(Collectors.toMap(
                        SysUser::getId,
                        user -> sysUserRoleMapper.findByUserId(user.getId())
                ));
        Set<UUID> roleIds = relationMap.values().stream()
                .flatMap(List::stream)
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysRole> roleMap = roleIds.isEmpty()
                ? Collections.emptyMap()
                : sysRoleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, role -> role));

        return users.stream()
                .filter(user -> scope.deptId() == null
                        || scope.allowCrossDept()
                        || Objects.equals(scope.deptId(), user.getDeptId()))
                .filter(user -> assignmentPolicy.matchesAssignableRole(
                        user.getId(),
                        relationMap,
                        roleMap,
                        allowedRoleCodes))
                .map(user -> toVO(user, relationMap))
                .toList();
    }

    public void assertAssignableUser(UUID targetUserId, List<String> currentRoleCodes, UUID currentDeptId) {
        assignmentPolicy.assertAssignableUser(targetUserId, currentRoleCodes, currentDeptId);
    }

    public void assertRecruiterUser(UUID targetUserId) {
        assignmentPolicy.assertRecruiterUser(targetUserId);
    }

    private SysUserVO toVO(SysUser user, Map<UUID, List<SysUserRole>> relationMap) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setForcePasswordChange(user.getForcePasswordChange());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoleIds(relationMap.getOrDefault(user.getId(), Collections.emptyList()).stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList()));
        return vo;
    }
}
