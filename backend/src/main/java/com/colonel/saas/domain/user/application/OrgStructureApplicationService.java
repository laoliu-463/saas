package com.colonel.saas.domain.user.application;

import com.colonel.saas.domain.user.policy.OrgAssignmentPolicy;
import com.colonel.saas.domain.user.policy.OrgAssignmentPolicy.ResolvedAssignment;
import com.colonel.saas.domain.user.policy.OrgAssignmentPolicy.SplitAssignment;
import com.colonel.saas.domain.user.policy.OrgEnrichmentPolicy;
import com.colonel.saas.domain.user.policy.OrgValidationPolicy;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 组织架构应用服务（DDD-USER-MIGRATION-005）。
 *
 * <p>用户域 / 组织架构的薄壳 facade，集成 3 个 Policy：
 * <ul>
 *   <li>{@link OrgAssignmentPolicy} - resolveAssignment / splitAssignment</li>
 *   <li>{@link OrgValidationPolicy} - validateGroupLeader / assertCanDeleteDept</li>
 *   <li>{@link OrgEnrichmentPolicy} - enrichUser / enrichUserList / formatOrgChangeRemark</li>
 * </ul>
 *
 * <p><b>行为 1:1 等价</b>于 OrgStructureService 旧实现（被 25 个 OrgStructureServiceTest
 * 用例 + 13 个本测试间接验证）。</p>
 *
 * <p>所属业务领域：用户域 / 组织架构</p>
 */
@Service
public class OrgStructureApplicationService {

    private final OrgAssignmentPolicy orgAssignmentPolicy;
    private final OrgValidationPolicy orgValidationPolicy;
    private final OrgEnrichmentPolicy orgEnrichmentPolicy;

    /**
     * 构造注入。ApplicationService 只编排组织架构策略，
     * 持久化访问由 Policy 依赖的端口适配器下沉到 infrastructure。
     */
    public OrgStructureApplicationService(
            OrgAssignmentPolicy orgAssignmentPolicy,
            OrgValidationPolicy orgValidationPolicy,
            OrgEnrichmentPolicy orgEnrichmentPolicy) {
        this.orgAssignmentPolicy = orgAssignmentPolicy;
        this.orgValidationPolicy = orgValidationPolicy;
        this.orgEnrichmentPolicy = orgEnrichmentPolicy;
    }

    // ===== OrgAssignmentPolicy 委托 =====

    public ResolvedAssignment resolveAssignment(UUID parentDeptId, UUID groupId) {
        return orgAssignmentPolicy.resolveAssignment(parentDeptId, groupId);
    }

    public SplitAssignment splitAssignment(UUID effectiveDeptId) {
        return orgAssignmentPolicy.splitAssignment(effectiveDeptId);
    }

    // ===== OrgEnrichmentPolicy 委托 =====

    public void enrichUserList(List<SysUserVO> users) {
        orgEnrichmentPolicy.enrichUserList(users);
    }

    public SysUserVO enrichUser(SysUserVO user) {
        return orgEnrichmentPolicy.enrichUser(user);
    }

    public String formatOrgChangeRemark(
            UUID userId,
            UUID oldEffectiveDeptId,
            UUID newEffectiveDeptId,
            UUID operatorId) {
        return orgEnrichmentPolicy.formatOrgChangeRemark(
                userId, oldEffectiveDeptId, newEffectiveDeptId, operatorId);
    }

    // ===== OrgValidationPolicy 委托 =====

    public String validateGroupLeader(UUID leaderUserId, String groupType) {
        return orgValidationPolicy.validateGroupLeader(leaderUserId, groupType);
    }

    public void assertCanDeleteDept(UUID deptId) {
        orgValidationPolicy.assertCanDeleteDept(deptId);
    }
}
