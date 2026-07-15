package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.port.UserAssignmentLookup;
import com.colonel.saas.domain.user.port.UserAssignmentLookup.AssignableRole;
import com.colonel.saas.domain.user.port.UserAssignmentLookup.AssignableUser;
import com.colonel.saas.domain.user.port.UserAssignmentLookup.UserRoleAssignment;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户分配校验策略（DDD-USER-MIGRATION-007，issue #16）。
 *
 * <p>从 {@code SysUserService.assertAssignableUser} 与
 * {@code SysUserService.assertRecruiterUser} 抽取的纯校验逻辑。</p>
 *
 * <p>本策略保留旧方法的所有行为契约：</p>
 * <ul>
 *   <li>{@link #assertAssignableUser}：管理员分配负责人时的可分配范围校验</li>
 *   <li>{@link #assertRecruiterUser}：管理员分配活动级招商组长时的招商身份校验</li>
 * </ul>
 *
 * <p>设计要点：</p>
 * <ol>
 *   <li>方法体 1:1 复刻旧 Service 的逻辑（不引入新行为，遵守 parity 纪律）</li>
 *   <li>通过 {@link UserAssignmentLookup} 读取目标用户与角色关系，避免 policy 直接依赖持久化类型</li>
 *   <li>内嵌 {@link AssignableScope} record，不复用 Service 私有类型
 *       （与 {@code OrgAssignmentPolicy} 内嵌 record 模式一致）</li>
 *   <li>内嵌角色常量集合，不依赖 Service 私有常量</li>
 * </ol>
 *
 * <p>后续 issue（如 #18 SysUserAssignmentApplication）会接入本策略，
 * 旧 Service 的同名方法可改为委派实现。</p>
 */
public class UserAssignmentPolicy {

    /** 可分配的业务角色编码集合（业务组长、专员、渠道组长、专员） */
    private static final Set<String> ASSIGNABLE_BIZ_ROLE_CODES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF,
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.CHANNEL_STAFF
    );

    /** 活动级分配可选的招商相关角色（与 /users/master-data/recruiters 一致） */
    private static final Set<String> RECRUITER_ROLE_CODES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF
    );

    private final UserAssignmentLookup assignmentLookup;

    public UserAssignmentPolicy(UserAssignmentLookup assignmentLookup) {
        this.assignmentLookup = assignmentLookup;
    }

    /**
     * 校验目标用户可被当前用户分配为负责人。
     *
     * <ol>
     *   <li>解析当前用户的可分配范围（角色 + 部门 + 跨部门权限）</li>
     *   <li>若范围为空则抛 stateInvalid（角色不允许分配）</li>
     *   <li>查询目标用户，校验部门归属（非跨部门模式下必须同部门）</li>
     *   <li>查询目标用户的角色关联关系，校验角色匹配（必须拥有可分配角色之一）</li>
     * </ol>
     *
     * @param targetUserId    目标用户 ID
     * @param currentRoleCodes 当前用户的角色编码列表
     * @param currentDeptId   当前用户的部门 ID
     * @throws BusinessException 目标用户 ID 为空、角色不允许分配、跨部门访问或角色不匹配时抛出
     */
    public void assertAssignableUser(UUID targetUserId, List<String> currentRoleCodes, UUID currentDeptId) {
        if (targetUserId == null) {
            throw BusinessException.param("负责人不能为空");
        }
        AssignableScope scope = resolveAssignableScope(currentRoleCodes, currentDeptId);
        if (scope.allowedRoleCodes().isEmpty()) {
            throw BusinessException.stateInvalid("当前角色不允许分配负责人");
        }
        AssignableUser targetUser = requireUser(targetUserId);
        if (scope.deptId() != null && !scope.allowCrossDept() && !Objects.equals(scope.deptId(), targetUser.deptId())) {
            throw BusinessException.forbidden("只能分配给本组招商下属");
        }

        List<UserRoleAssignment> relations = assignmentLookup.findUserRoles(targetUserId);
        if (relations == null || relations.isEmpty()) {
            throw BusinessException.stateInvalid("目标负责人未配置可分配角色");
        }
        Set<UUID> roleIds = relations.stream()
                .map(UserRoleAssignment::roleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, AssignableRole> roleMap = assignmentLookup.findRolesByIds(roleIds);
        if (!matchesAssignableRole(targetUserId, Map.of(targetUserId, relations), roleMap, scope.allowedRoleCodes())) {
            throw BusinessException.forbidden("只能分配给符合规则的招商下属");
        }
    }

    /**
     * 校验目标用户可作为活动级招商组长（管理员分配活动专用）。
     *
     * <ol>
     *   <li>目标用户必须存在</li>
     *   <li>目标用户必须处于 ACTIVE 状态</li>
     *   <li>目标用户必须配置招商相关角色（BIZ_LEADER / BIZ_STAFF）</li>
     * </ol>
     *
     * @param targetUserId 目标用户 ID
     * @throws BusinessException 目标用户 ID 为空、未启用、未配置招商角色或角色不匹配时抛出
     */
    public void assertRecruiterUser(UUID targetUserId) {
        if (targetUserId == null) {
            throw BusinessException.param("assigneeId 不能为空");
        }
        AssignableUser targetUser = requireUser(targetUserId);
        if (targetUser.status() == null || targetUser.status() != SysUserStatus.ACTIVE) {
            throw BusinessException.stateInvalid("目标用户未启用");
        }
        List<UserRoleAssignment> relations = assignmentLookup.findUserRoles(targetUserId);
        if (relations == null || relations.isEmpty()) {
            throw BusinessException.stateInvalid("目标用户未配置招商角色");
        }
        Set<UUID> roleIds = relations.stream()
                .map(UserRoleAssignment::roleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, AssignableRole> roleMap = assignmentLookup.findRolesByIds(roleIds);
        if (!matchesAssignableRole(targetUserId, Map.of(targetUserId, relations), roleMap, RECRUITER_ROLE_CODES)) {
            throw BusinessException.forbidden("只能分配给招商组长、招商专员或招商组长兼容角色");
        }
    }

    private AssignableUser requireUser(UUID id) {
        return assignmentLookup.findUser(id)
                .filter(user -> user.deleted() == null || user.deleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    /**
     * 解析当前用户的可分配范围。
     *
     * <ul>
     *   <li>ADMIN：可分配所有业务角色，不限部门，允许跨部门</li>
     *   <li>BIZ_LEADER：只能分配 BIZ_STAFF，限同部门</li>
     *   <li>CHANNEL_LEADER：只能分配 CHANNEL_STAFF，限同部门</li>
     *   <li>其他角色：返回空范围（不允许分配负责人）</li>
     * </ul>
     */
    public AssignableScope resolveAssignableScope(List<String> currentRoleCodes, UUID currentDeptId) {
        if (currentRoleCodes == null || currentRoleCodes.isEmpty()) {
            return AssignableScope.empty();
        }
        LinkedHashSet<String> normalized = currentRoleCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.contains(RoleCodes.ADMIN)) {
            return new AssignableScope(ASSIGNABLE_BIZ_ROLE_CODES, null, true);
        }
        if (normalized.contains(RoleCodes.BIZ_LEADER)) {
            return new AssignableScope(Set.of(RoleCodes.BIZ_STAFF), currentDeptId, false);
        }
        if (normalized.contains(RoleCodes.CHANNEL_LEADER)) {
            return new AssignableScope(Set.of(RoleCodes.CHANNEL_STAFF), currentDeptId, false);
        }
        return AssignableScope.empty();
    }

    /**
     * 判断用户是否拥有可分配角色中的任意一个。
     * 遍历用户的角色关联关系，跳过已禁用或不存在的角色。
     */
    public boolean matchesAssignableRole(
            UUID userId,
            Map<UUID, List<UserRoleAssignment>> relationMap,
            Map<UUID, AssignableRole> roleMap,
            Set<String> allowedRoleCodes) {
        List<UserRoleAssignment> relations = relationMap.getOrDefault(userId, Collections.emptyList());
        if (relations.isEmpty()) {
            return false;
        }
        for (UserRoleAssignment relation : relations) {
            AssignableRole role = roleMap.get(relation.roleId());
            if (role == null || role.status() == null || role.status() != 1) {
                continue;
            }
            if (allowedRoleCodes.contains(role.roleCode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 可分配范围描述（内嵌 record，独立命名空间，不复用 SysUserService 私有类型）。
     */
    public record AssignableScope(Set<String> allowedRoleCodes, UUID deptId, boolean allowCrossDept) {
        /** 创建空的可分配范围（不允许任何分配）。 */
        public static AssignableScope empty() {
            return new AssignableScope(Collections.emptySet(), null, false);
        }
    }
}
