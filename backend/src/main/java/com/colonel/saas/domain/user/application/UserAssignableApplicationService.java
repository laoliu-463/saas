package com.colonel.saas.domain.user.application;

import com.colonel.saas.domain.user.policy.UserAssignmentPolicy;
import com.colonel.saas.domain.user.policy.UserAssignmentPolicy.AssignableScope;
import com.colonel.saas.domain.user.port.UserAssignmentLookup;
import com.colonel.saas.domain.user.port.UserAssignmentLookup.AssignableRole;
import com.colonel.saas.domain.user.port.UserAssignmentLookup.UserRoleAssignment;
import com.colonel.saas.domain.user.port.UserAssignableCandidateLookup;
import com.colonel.saas.domain.user.port.UserAssignableCandidateLookup.AssignableCandidate;
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

    private static final int CANDIDATE_LIMIT = 20;

    private final UserAssignableCandidateLookup candidateLookup;
    private final UserAssignmentLookup assignmentLookup;
    private final UserAssignmentPolicy assignmentPolicy;

    public UserAssignableApplicationService(
            UserAssignableCandidateLookup candidateLookup,
            UserAssignmentLookup assignmentLookup,
            UserAssignmentPolicy assignmentPolicy) {
        this.candidateLookup = candidateLookup;
        this.assignmentLookup = assignmentLookup;
        this.assignmentPolicy = assignmentPolicy;
    }

    public List<SysUserVO> findAssignableUsers(String keyword, List<String> currentRoleCodes, UUID currentDeptId) {
        List<AssignableCandidate> candidates = candidateLookup.findActiveCandidates(keyword, CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        AssignableScope scope = assignmentPolicy.resolveAssignableScope(currentRoleCodes, currentDeptId);
        Set<String> allowedRoleCodes = scope.allowedRoleCodes();
        if (allowedRoleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, List<UserRoleAssignment>> relationMap = candidates.stream()
                .collect(Collectors.toMap(
                        AssignableCandidate::id,
                        candidate -> assignmentLookup.findUserRoles(candidate.id())
                ));
        Set<UUID> roleIds = relationMap.values().stream()
                .flatMap(List::stream)
                .map(UserRoleAssignment::roleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, AssignableRole> roleMap = assignmentLookup.findRolesByIds(roleIds);
        Map<UUID, List<UUID>> roleIdsByUserId = roleIdsByUserId(relationMap);

        return candidates.stream()
                .filter(candidate -> scope.deptId() == null
                        || scope.allowCrossDept()
                        || Objects.equals(scope.deptId(), candidate.deptId()))
                .filter(candidate -> assignmentPolicy.matchesAssignableRole(
                        candidate.id(),
                        relationMap,
                        roleMap,
                        allowedRoleCodes))
                .map(candidate -> candidateLookup.toVO(candidate, roleIdsByUserId))
                .toList();
    }

    public void assertAssignableUser(UUID targetUserId, List<String> currentRoleCodes, UUID currentDeptId) {
        assignmentPolicy.assertAssignableUser(targetUserId, currentRoleCodes, currentDeptId);
    }

    public void assertRecruiterUser(UUID targetUserId) {
        assignmentPolicy.assertRecruiterUser(targetUserId);
    }

    private Map<UUID, List<UUID>> roleIdsByUserId(Map<UUID, List<UserRoleAssignment>> relationMap) {
        return relationMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(UserRoleAssignment::roleId)
                                .collect(Collectors.toList())
                ));
    }
}
