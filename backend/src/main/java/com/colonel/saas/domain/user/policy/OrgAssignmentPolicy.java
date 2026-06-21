package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.domain.user.port.OrgNodeLookup;
import com.colonel.saas.domain.user.port.OrgNodeLookup.OrgNode;

import java.util.Objects;
import java.util.UUID;

/**
 * 组织归属解析策略（DDD-USER-MIGRATION-002）。
 *
 * <p>从 {@code OrgStructureService.resolveAssignment} 和 {@code splitAssignment}
 * 提取出来的纯函数策略，负责组织归属的双向翻译：
 * <ul>
 *   <li>{@link #resolveAssignment(UUID, UUID)}：前端入参 → 有效 dept_id</li>
 *   <li>{@link #splitAssignment(UUID)}：有效 dept_id → 前端展示字段</li>
 * </ul>
 *
 * <p><b>行为 1:1 等价</b>于 OrgStructureService 旧实现（被 25 个 OrgStructureServiceTest
 * 用例和 11 个 OrgAssignmentPolicyTest 用例共同验证）。</p>
 *
 * <p>所属业务领域：用户域 / 组织架构</p>
 *
 * @see com.colonel.saas.constant.DeptType
 */
public class OrgAssignmentPolicy {

    private final OrgNodeLookup orgNodeLookup;

    public OrgAssignmentPolicy(OrgNodeLookup orgNodeLookup) {
        this.orgNodeLookup = orgNodeLookup;
    }

    /**
     * 组织归属解析结果。
     *
     * @param effectiveDeptId 解析后的有效 dept_id（存入 sys_user.dept_id），可为 null
     * @param parentDeptId    所属部门 ID，可为 null
     * @param groupId         所属业务组 ID，可为 null
     */
    public record ResolvedAssignment(UUID effectiveDeptId, UUID parentDeptId, UUID groupId) {
    }

    /**
     * 组织归属拆分结果，用于前端展示和审计日志。
     *
     * @param parentDeptId   所属部门 ID，可为 null
     * @param groupId        所属业务组 ID，可为 null
     * @param parentDeptName 所属部门名称，可为 null
     * @param groupName      所属业务组名称，可为 null
     * @param groupType      组别类型编码，可为 null
     */
    public record SplitAssignment(
            UUID parentDeptId, UUID groupId, String parentDeptName, String groupName, String groupType) {
    }

    /**
     * 解析前端传入的组织归属参数为有效的 dept_id。
     */
    public ResolvedAssignment resolveAssignment(UUID parentDeptId, UUID groupId) {
        if (groupId != null) {
            OrgNode group = requireActiveDept(groupId);
            if (!DeptType.isGroup(group.type())) {
                throw BusinessException.param("所属组别必须为招商组/渠道组/运营组");
            }
            if (parentDeptId != null && !Objects.equals(group.parentId(), parentDeptId)) {
                throw BusinessException.param("所选组别不属于当前部门");
            }
            UUID resolvedParent = group.parentId() != null ? group.parentId() : parentDeptId;
            return new ResolvedAssignment(groupId, resolvedParent, groupId);
        }
        if (parentDeptId != null) {
            OrgNode dept = requireActiveDept(parentDeptId);
            if (!DeptType.isDepartment(dept.type())
                    && !DeptType.OPS_GROUP.equals(DeptType.normalize(dept.type()))) {
                throw BusinessException.param("所属部门类型无效");
            }
            return new ResolvedAssignment(parentDeptId, parentDeptId, null);
        }
        return new ResolvedAssignment(null, null, null);
    }

    /**
     * 将 effectiveDeptId 反向拆分为部门和组别信息。
     */
    public SplitAssignment splitAssignment(UUID effectiveDeptId) {
        if (effectiveDeptId == null) {
            return new SplitAssignment(null, null, null, null, null);
        }
        OrgNode node = orgNodeLookup.findById(effectiveDeptId).orElse(null);
        if (node == null || node.deleted()) {
            return new SplitAssignment(null, null, null, null, null);
        }
        if (DeptType.isGroup(node.type())) {
            OrgNode parent = node.parentId() == null ? null : orgNodeLookup.findById(node.parentId()).orElse(null);
            return new SplitAssignment(
                    node.parentId(),
                    node.id(),
                    parent == null ? null : parent.name(),
                    node.name(),
                    DeptType.normalize(node.type()));
        }
        return new SplitAssignment(
                node.id(),
                null,
                node.name(),
                null,
                DeptType.normalize(node.type()));
    }

    /**
     * 查询并校验部门/组别存在且未被删除。
     */
    private OrgNode requireActiveDept(UUID id) {
        OrgNode dept = orgNodeLookup.findById(id).orElse(null);
        if (dept == null || dept.deleted()) {
            throw BusinessException.notFound("部门或组别不存在");
        }
        return dept;
    }
}
