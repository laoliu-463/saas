package com.colonel.saas.domain.user.policy;

import com.colonel.saas.constant.DeptType;
import com.colonel.saas.domain.user.port.OrgEnrichmentLookup;
import com.colonel.saas.domain.user.port.OrgEnrichmentLookup.RoleSummary;
import com.colonel.saas.domain.user.port.OrgNodeLookup;
import com.colonel.saas.domain.user.port.OrgNodeLookup.OrgNode;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrgEnrichmentPolicy 单测（DDD-USER-MIGRATION-004）。
 *
 * <p>覆盖 enrichUser + enrichUserList + formatOrgChangeRemark 三个方法。
 * formatOrgChangeRemark 委托给 OrgAssignmentPolicy.splitAssignment（避免重复实现）。</p>
 */
class OrgEnrichmentPolicyTest {

    private InMemoryOrgNodeLookup orgNodes;
    private InMemoryOrgEnrichmentLookup enrichmentLookup;
    private OrgAssignmentPolicy assignmentPolicy;
    private OrgEnrichmentPolicy policy;

    @BeforeEach
    void setUp() {
        orgNodes = new InMemoryOrgNodeLookup();
        enrichmentLookup = new InMemoryOrgEnrichmentLookup();
        assignmentPolicy = new OrgAssignmentPolicy(orgNodes);
        policy = new OrgEnrichmentPolicy(enrichmentLookup, assignmentPolicy);
    }

    // ===== enrichUser =====

    @Test
    void enrichUser_nullInput_shouldReturnNull() {
        assertThat(policy.enrichUser(null)).isNull();
    }

    @Test
    void enrichUser_withGroupDept_shouldFillOrgFields() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        enrichmentLookup.addOrgNode(new OrgNode(
                parentId, null, "渠道部", DeptType.DEPARTMENT, false));
        enrichmentLookup.addOrgNode(new OrgNode(
                deptId, parentId, "A 组", DeptType.CHANNEL_GROUP, false));

        SysUserVO user = new SysUserVO();
        user.setId(userId);
        user.setDeptId(deptId);
        user.setRoleIds(new ArrayList<>());

        policy.enrichUser(user);

        assertThat(user.getGroupId()).isEqualTo(deptId);
        assertThat(user.getGroupName()).isEqualTo("A 组");
        assertThat(user.getGroupType()).isEqualTo("channel_group");
        assertThat(user.getParentDeptId()).isEqualTo(parentId);
        assertThat(user.getParentDeptName()).isEqualTo("渠道部");
    }

    @Test
    void enrichUser_withPrimaryRole_shouldFillRoleFields() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        enrichmentLookup.addRole(new RoleSummary(roleId, "biz_leader", "招商组长"));

        SysUserVO user = new SysUserVO();
        user.setId(userId);
        user.setDeptId(deptId);
        user.setRoleIds(new ArrayList<>(List.of(roleId)));

        policy.enrichUser(user);

        assertThat(user.getRoleId()).isEqualTo(roleId);
        assertThat(user.getRoleCode()).isEqualTo("biz_leader");
        assertThat(user.getRoleName()).isEqualTo("招商组长");
    }

    // ===== enrichUserList =====

    @Test
    void enrichUserList_emptyList_shouldNoOp() {
        policy.enrichUserList(Collections.emptyList());
        // 不抛异常即可
    }

    @Test
    void enrichUserList_nullList_shouldNoOp() {
        policy.enrichUserList(null);
        // 不抛异常即可
    }

    @Test
    void enrichUserList_multipleUsers_shouldFillAll() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID deptId1 = UUID.randomUUID();
        UUID deptId2 = UUID.randomUUID();

        enrichmentLookup.addOrgNode(new OrgNode(
                deptId1, null, "招商部", DeptType.DEPARTMENT, false));
        enrichmentLookup.addOrgNode(new OrgNode(
                deptId2, null, "渠道部", DeptType.DEPARTMENT, false));

        SysUserVO user1 = new SysUserVO();
        user1.setId(userId1);
        user1.setDeptId(deptId1);
        user1.setRoleIds(new ArrayList<>());
        SysUserVO user2 = new SysUserVO();
        user2.setId(userId2);
        user2.setDeptId(deptId2);
        user2.setRoleIds(new ArrayList<>());

        policy.enrichUserList(List.of(user1, user2));

        assertThat(user1.getParentDeptId()).isEqualTo(deptId1);
        assertThat(user1.getParentDeptName()).isEqualTo("招商部");
        assertThat(user2.getParentDeptId()).isEqualTo(deptId2);
        assertThat(user2.getParentDeptName()).isEqualTo("渠道部");
    }

    // ===== formatOrgChangeRemark =====

    @Test
    void formatOrgChangeRemark_nullDeptIds_shouldStillFormat() {
        UUID userId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        String remark = policy.formatOrgChangeRemark(userId, null, null, operatorId);
        assertThat(remark).contains("userId=" + userId);
        assertThat(remark).contains("operatorId=" + operatorId);
        assertThat(remark).contains("oldDeptId=null");
        assertThat(remark).contains("newDeptId=null");
    }

    @Test
    void formatOrgChangeRemark_withDeptChanges_shouldIncludeAllFields() {
        UUID userId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID oldDept = UUID.randomUUID();
        UUID newDept = UUID.randomUUID();
        orgNodes.add(new OrgNode(oldDept, null, "旧部门", DeptType.DEPARTMENT, false));
        orgNodes.add(new OrgNode(newDept, null, "新部门", DeptType.DEPARTMENT, false));

        String remark = policy.formatOrgChangeRemark(userId, oldDept, newDept, operatorId);
        assertThat(remark).contains("oldDeptId=" + oldDept);
        assertThat(remark).contains("newDeptId=" + newDept);
        assertThat(remark).contains("userId=" + userId);
    }

    @Test
    void source_shouldNotDependOnPersistenceMapperOrEntity() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/policy/OrgEnrichmentPolicy.java"));

        assertThat(source).doesNotContain("mapper.SysDeptMapper");
        assertThat(source).doesNotContain("mapper.SysRoleMapper");
        assertThat(source).doesNotContain("entity.SysDept");
        assertThat(source).doesNotContain("entity.SysRole");
    }

    private static final class InMemoryOrgEnrichmentLookup implements OrgEnrichmentLookup {
        private final List<OrgNode> orgNodes = new ArrayList<>();
        private final List<RoleSummary> roles = new ArrayList<>();

        void addOrgNode(OrgNode node) {
            orgNodes.add(node);
        }

        void addRole(RoleSummary role) {
            roles.add(role);
        }

        @Override
        public List<OrgNode> findActiveOrgNodes() {
            return orgNodes;
        }

        @Override
        public List<RoleSummary> findRoles() {
            return roles;
        }
    }

    private static final class InMemoryOrgNodeLookup implements OrgNodeLookup {
        private final Map<UUID, OrgNode> nodes = new HashMap<>();

        void add(OrgNode node) {
            nodes.put(node.id(), node);
        }

        @Override
        public Optional<OrgNode> findById(UUID orgNodeId) {
            return Optional.ofNullable(nodes.get(orgNodeId));
        }
    }
}
