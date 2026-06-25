package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.port.OrgDeletionConstraintLookup;
import com.colonel.saas.domain.user.port.OrgLeaderCandidateLookup;
import com.colonel.saas.domain.user.port.OrgLeaderCandidateLookup.LeaderCandidate;

import java.util.Set;
import java.util.UUID;

/**
 * 组织校验策略（DDD-USER-MIGRATION-003）。
 *
 * <p>从 {@code OrgStructureService.validateGroupLeader} 和 {@code assertCanDeleteDept}
 * 提取出来的纯函数策略，负责：
 * <ul>
 *   <li>组长角色与组别类型的匹配关系校验</li>
 *   <li>部门/组别可删除性校验（无员工、无子组）</li>
 * </ul>
 *
 * <p><b>行为 1:1 等价</b>于 OrgStructureService 旧实现。</p>
 *
 * <p>所属业务领域：用户域 / 组织架构</p>
 */
public class OrgValidationPolicy {

    /** 招商组允许的组长角色集合 */
    private static final Set<String> RECRUITER_LEADER_ROLES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.ADMIN);

    /** 渠道组允许的组长角色集合 */
    private static final Set<String> CHANNEL_LEADER_ROLES = Set.of(
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.ADMIN);

    /** 运营组允许的组长角色集合 */
    private static final Set<String> OPS_LEADER_ROLES = Set.of(
            RoleCodes.OPS_STAFF,
            RoleCodes.ADMIN);

    /** 部门负责人允许使用各业务线组长角色 */
    private static final Set<String> DEPARTMENT_LEADER_ROLES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.OPS_STAFF,
            RoleCodes.ADMIN);

    private final OrgLeaderCandidateLookup leaderCandidateLookup;
    private final OrgDeletionConstraintLookup deletionConstraintLookup;
    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;

    public OrgValidationPolicy(
            OrgLeaderCandidateLookup leaderCandidateLookup,
            OrgDeletionConstraintLookup deletionConstraintLookup,
            CurrentUserPermissionPolicy currentUserPermissionPolicy) {
        this.leaderCandidateLookup = leaderCandidateLookup;
        this.deletionConstraintLookup = deletionConstraintLookup;
        this.currentUserPermissionPolicy = currentUserPermissionPolicy;
    }

    /**
     * 校验组长角色与组别类型的匹配关系。
     */
    public String validateGroupLeader(UUID leaderUserId, String groupType) {
        if (leaderUserId == null) {
            return null;
        }
        LeaderCandidate leader = leaderCandidateLookup.findActiveLeaderCandidate(leaderUserId)
                .orElseThrow(() -> BusinessException.param("组长必须是系统内有效用户"));
        String normalizedType = DeptType.normalize(groupType);
        Set<String> allowed = switch (normalizedType) {
            case DeptType.RECRUITER_GROUP -> RECRUITER_LEADER_ROLES;
            case DeptType.CHANNEL_GROUP -> CHANNEL_LEADER_ROLES;
            case DeptType.OPS_GROUP -> OPS_LEADER_ROLES;
            case DeptType.DEPARTMENT -> DEPARTMENT_LEADER_ROLES;
            default -> Set.of(RoleCodes.ADMIN);
        };
        if (!currentUserPermissionPolicy.hasAnyRole(leader.roleCodes(), allowed.toArray(String[]::new))) {
            throw BusinessException.param("组长角色与组别类型不匹配");
        }
        if (hasText(leader.realName())) {
            return leader.realName();
        }
        if (hasText(leader.username())) {
            return leader.username();
        }
        return leaderUserId.toString();
    }

    /**
     * 校验部门或组别是否可以安全删除。
     */
    public void assertCanDeleteDept(UUID deptId) {
        long directUsers = deletionConstraintLookup.countDirectUsers(deptId);
        if (directUsers > 0) {
            throw BusinessException.stateInvalid("部门或组别下仍有员工，无法删除");
        }
        long childGroups = deletionConstraintLookup.countChildGroups(deptId);
        if (childGroups > 0) {
            throw BusinessException.stateInvalid("请先删除下级组别");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
