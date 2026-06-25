package com.colonel.saas.auth.service;

import com.colonel.saas.domain.user.application.OrgStructureApplicationService;
import com.colonel.saas.domain.user.policy.OrgAssignmentPolicy;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 组织架构服务（Legacy 委派壳，DDD-USER-MIGRATION-005）。
 *
 * <p>本类已迁移到 {@code domain.user.application.OrgStructureApplicationService}，
 * 现作为薄壳 facade 保留向后兼容的 public 方法签名。
 * 所有业务逻辑委托给用户域应用服务。</p>
 *
 * <p><b>迁移路径</b>：
 * <ul>
 *   <li>resolveAssignment / splitAssignment → OrgStructureApplicationService</li>
 *   <li>validateGroupLeader / assertCanDeleteDept → OrgStructureApplicationService</li>
 *   <li>enrichUser / enrichUserList / formatOrgChangeRemark → OrgStructureApplicationService</li>
 * </ul>
 *
 * <p><b>保留 API</b>：
 * <ul>
 *   <li>{@link ResolvedAssignment} / {@link SplitAssignment} record 保留（SysUserService 直接 import）</li>
 *   <li>8 个 public 方法签名保留（外部调用方零改动）</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 组织架构（Legacy）</p>
 */
@Service
public class OrgStructureService {

    private final OrgStructureApplicationService applicationService;

    /**
     * Legacy 构造器（DDD-USER-MIGRATION-005）。
     *
     * <p>生产代码通过 Spring 注入用户域应用服务；本类只保留旧包名和旧 public API。</p>
     */
    public OrgStructureService(OrgStructureApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 组织归属解析结果。
     *
     * <p>作为 {@link OrgAssignmentPolicy.ResolvedAssignment} 的类型转发器，
     * 保留旧 record 名称以兼容 {@code SysUserService} 等外部直接 import。</p>
     */
    public record ResolvedAssignment(UUID effectiveDeptId, UUID parentDeptId, UUID groupId) {
        public static ResolvedAssignment from(OrgAssignmentPolicy.ResolvedAssignment src) {
            return new ResolvedAssignment(src.effectiveDeptId(), src.parentDeptId(), src.groupId());
        }
    }

    /**
     * 组织归属拆分结果。
     *
     * <p>作为 {@link OrgAssignmentPolicy.SplitAssignment} 的类型转发器，
     * 保留旧 record 名称以兼容外部直接 import。</p>
     */
    public record SplitAssignment(
            UUID parentDeptId, UUID groupId, String parentDeptName, String groupName, String groupType) {
        public static SplitAssignment from(OrgAssignmentPolicy.SplitAssignment src) {
            return new SplitAssignment(
                    src.parentDeptId(), src.groupId(),
                    src.parentDeptName(), src.groupName(), src.groupType());
        }
    }

    /**
     * 解析前端传入的组织归属参数为有效的 dept_id。
     */
    public ResolvedAssignment resolveAssignment(UUID parentDeptId, UUID groupId) {
        return ResolvedAssignment.from(applicationService.resolveAssignment(parentDeptId, groupId));
    }

    /**
     * 将 effectiveDeptId 反向拆分为部门和组别信息。
     */
    public SplitAssignment splitAssignment(UUID effectiveDeptId) {
        return SplitAssignment.from(applicationService.splitAssignment(effectiveDeptId));
    }

    /**
     * 批量为用户列表填充组织归属字段。
     */
    public void enrichUserList(List<SysUserVO> users) {
        applicationService.enrichUserList(users);
    }

    /**
     * 为单个用户填充组织归属字段。
     */
    public SysUserVO enrichUser(SysUserVO user) {
        return applicationService.enrichUser(user);
    }

    /**
     * 校验组长角色与组别类型的匹配关系。
     */
    public String validateGroupLeader(UUID leaderUserId, String groupType) {
        return applicationService.validateGroupLeader(leaderUserId, groupType);
    }

    /**
     * 校验部门或组别是否可以安全删除。
     */
    public void assertCanDeleteDept(UUID deptId) {
        applicationService.assertCanDeleteDept(deptId);
    }

    /**
     * 生成组织变更的审计备注文本。
     */
    public String formatOrgChangeRemark(
            UUID userId,
            UUID oldEffectiveDeptId,
            UUID newEffectiveDeptId,
            UUID operatorId) {
        return applicationService.formatOrgChangeRemark(
                userId, oldEffectiveDeptId, newEffectiveDeptId, operatorId);
    }
}
