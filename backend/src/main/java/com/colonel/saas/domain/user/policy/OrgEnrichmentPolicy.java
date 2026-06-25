package com.colonel.saas.domain.user.policy;

import com.colonel.saas.constant.DeptType;
import com.colonel.saas.domain.user.port.OrgEnrichmentLookup;
import com.colonel.saas.domain.user.port.OrgEnrichmentLookup.RoleSummary;
import com.colonel.saas.domain.user.port.OrgNodeLookup.OrgNode;
import com.colonel.saas.domain.user.policy.OrgAssignmentPolicy.SplitAssignment;
import com.colonel.saas.vo.SysUserVO;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 组织数据填充策略（DDD-USER-MIGRATION-004）。
 *
 * <p>从 {@code OrgStructureService.enrichUser / enrichUserList / formatOrgChangeRemark}
 * 提取出来的策略，负责：
 * <ul>
 *   <li>为用户列表/单个用户填充组织归属字段（部门名称、组名、组类型）</li>
 *   <li>为用户列表/单个用户填充主角色编码和名称</li>
 *   <li>生成组织变更的审计备注文本（委托给 OrgAssignmentPolicy.splitAssignment）</li>
 * </ul>
 *
 * <p><b>行为 1:1 等价</b>于 OrgStructureService 旧实现。</p>
 *
 * <p>所属业务领域：用户域 / 组织架构</p>
 */
public class OrgEnrichmentPolicy {

    private final OrgEnrichmentLookup orgEnrichmentLookup;
    private final OrgAssignmentPolicy orgAssignmentPolicy;

    public OrgEnrichmentPolicy(
            OrgEnrichmentLookup orgEnrichmentLookup,
            OrgAssignmentPolicy orgAssignmentPolicy) {
        this.orgEnrichmentLookup = orgEnrichmentLookup;
        this.orgAssignmentPolicy = orgAssignmentPolicy;
    }

    /**
     * 批量为用户列表填充组织归属字段。
     */
    public void enrichUserList(List<SysUserVO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<UUID, OrgNode> deptMap = loadDeptMap();
        Map<UUID, RoleSummary> roleMap = loadRoleMap();
        for (SysUserVO user : users) {
            applyOrgFields(user, deptMap);
            applyPrimaryRole(user, roleMap);
        }
    }

    /**
     * 为单个用户填充组织归属字段（委托给 enrichUserList）。
     */
    public SysUserVO enrichUser(SysUserVO user) {
        if (user == null) {
            return null;
        }
        enrichUserList(List.of(user));
        return user;
    }

    /**
     * 生成组织变更的审计备注文本。
     *
     * <p>委托给 OrgAssignmentPolicy.splitAssignment 获取旧/新部门拆分信息。</p>
     */
    public String formatOrgChangeRemark(
            UUID userId,
            UUID oldEffectiveDeptId,
            UUID newEffectiveDeptId,
            UUID operatorId) {
        SplitAssignment oldSplit = orgAssignmentPolicy.splitAssignment(oldEffectiveDeptId);
        SplitAssignment newSplit = orgAssignmentPolicy.splitAssignment(newEffectiveDeptId);
        return String.format(
                "userId=%s operatorId=%s oldDeptId=%s oldGroupId=%s newDeptId=%s newGroupId=%s",
                userId,
                operatorId,
                oldSplit.parentDeptId(),
                oldSplit.groupId(),
                newSplit.parentDeptId(),
                newSplit.groupId());
    }

    /**
     * 根据用户 dept_id 填充组织归属展示字段。
     */
    private void applyOrgFields(SysUserVO user, Map<UUID, OrgNode> deptMap) {
        if (user.getDeptId() == null) {
            return;
        }
        OrgNode assigned = deptMap.get(user.getDeptId());
        if (assigned == null) {
            return;
        }
        if (DeptType.isGroup(assigned.type())) {
            user.setGroupId(assigned.id());
            user.setGroupName(assigned.name());
            user.setGroupType(DeptType.normalize(assigned.type()));
            if (assigned.parentId() != null) {
                OrgNode parent = deptMap.get(assigned.parentId());
                user.setParentDeptId(assigned.parentId());
                user.setParentDeptName(parent == null ? null : parent.name());
            }
        } else {
            user.setParentDeptId(assigned.id());
            user.setParentDeptName(assigned.name());
            user.setGroupType(DeptType.normalize(assigned.type()));
        }
    }

    /**
     * 根据用户角色 ID 列表填充主角色展示字段。
     */
    private void applyPrimaryRole(SysUserVO user, Map<UUID, RoleSummary> roleMap) {
        List<UUID> roleIds = user.getRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        RoleSummary role = roleMap.get(roleIds.get(0));
        if (role != null) {
            user.setRoleId(role.id());
            user.setRoleCode(role.code());
            user.setRoleName(role.name());
        }
    }

    /**
     * 加载全部有效部门/组别数据到内存 Map。
     */
    private Map<UUID, OrgNode> loadDeptMap() {
        List<OrgNode> depts = orgEnrichmentLookup.findActiveOrgNodes();
        if (depts == null) {
            return Collections.emptyMap();
        }
        Map<UUID, OrgNode> map = new HashMap<>();
        for (OrgNode dept : depts) {
            if (dept.id() != null && !dept.deleted()) {
                map.put(dept.id(), dept);
            }
        }
        return map;
    }

    /**
     * 加载全部角色数据到内存 Map。
     */
    private Map<UUID, RoleSummary> loadRoleMap() {
        List<RoleSummary> roles = orgEnrichmentLookup.findRoles();
        if (roles == null) {
            return Collections.emptyMap();
        }
        Map<UUID, RoleSummary> map = new HashMap<>();
        for (RoleSummary role : roles) {
            if (role.id() != null) {
                map.put(role.id(), role);
            }
        }
        return map;
    }
}
