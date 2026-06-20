package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.domain.user.port.OrgNodeLookup;
import com.colonel.saas.domain.user.policy.OrgAssignmentPolicy.ResolvedAssignment;
import com.colonel.saas.domain.user.policy.OrgAssignmentPolicy.SplitAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrgAssignmentPolicy 单测（DDD-USER-MIGRATION-002）。
 *
 * <p>覆盖 resolveAssignment + splitAssignment 两个方法。行为必须与 OrgStructureService 旧实现
 * 完全一致（25 个 OrgStructureServiceTest 用例是 Parity 基线）。</p>
 */
class OrgAssignmentPolicyTest {

    private InMemoryOrgNodeLookup orgNodes;
    private OrgAssignmentPolicy policy;

    @BeforeEach
    void setUp() {
        orgNodes = new InMemoryOrgNodeLookup();
        policy = new OrgAssignmentPolicy(orgNodes);
    }

    // ===== resolveAssignment =====

    @Test
    void resolveAssignment_groupOnlyValid_shouldReturnGroup() {
        UUID groupId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        orgNodes.add(new OrgNodeLookup.OrgNode(
                groupId, parentId, "A 组", DeptType.RECRUITER_GROUP, false));

        ResolvedAssignment result = policy.resolveAssignment(null, groupId);
        assertThat(result.effectiveDeptId()).isEqualTo(groupId);
        assertThat(result.parentDeptId()).isEqualTo(parentId);
        assertThat(result.groupId()).isEqualTo(groupId);
    }

    @Test
    void resolveAssignment_groupNotBelongToDept_shouldThrow() {
        UUID groupId = UUID.randomUUID();
        UUID wrongParentId = UUID.randomUUID();
        orgNodes.add(new OrgNodeLookup.OrgNode(
                groupId, UUID.randomUUID(), "A 组", DeptType.RECRUITER_GROUP, false));

        assertThatThrownBy(() -> policy.resolveAssignment(wrongParentId, groupId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolveAssignment_parentOnlyDepartment_shouldReturnDept() {
        UUID deptId = UUID.randomUUID();
        orgNodes.add(new OrgNodeLookup.OrgNode(
                deptId, null, "渠道部", DeptType.DEPARTMENT, false));

        ResolvedAssignment result = policy.resolveAssignment(deptId, null);
        assertThat(result.effectiveDeptId()).isEqualTo(deptId);
        assertThat(result.parentDeptId()).isEqualTo(deptId);
        assertThat(result.groupId()).isNull();
    }

    @Test
    void resolveAssignment_bothNull_shouldReturnAllNull() {
        ResolvedAssignment result = policy.resolveAssignment(null, null);
        assertThat(result.effectiveDeptId()).isNull();
        assertThat(result.parentDeptId()).isNull();
        assertThat(result.groupId()).isNull();
    }

    @Test
    void resolveAssignment_groupTypeNotGroup_shouldThrow() {
        UUID deptId = UUID.randomUUID();
        orgNodes.add(new OrgNodeLookup.OrgNode(
                deptId, null, "渠道部", DeptType.DEPARTMENT, false));

        assertThatThrownBy(() -> policy.resolveAssignment(null, deptId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolveAssignment_parentOpsGroup_shouldReturnDept() {
        UUID opsId = UUID.randomUUID();
        orgNodes.add(new OrgNodeLookup.OrgNode(
                opsId, null, "运营组", DeptType.OPS_GROUP, false));

        ResolvedAssignment result = policy.resolveAssignment(opsId, null);
        assertThat(result.effectiveDeptId()).isEqualTo(opsId);
        assertThat(result.parentDeptId()).isEqualTo(opsId);
        assertThat(result.groupId()).isNull();
    }

    // ===== splitAssignment =====

    @Test
    void splitAssignment_nullInput_shouldReturnAllNull() {
        SplitAssignment result = policy.splitAssignment(null);
        assertThat(result.parentDeptId()).isNull();
        assertThat(result.groupId()).isNull();
    }

    @Test
    void splitAssignment_groupType_shouldReturnParentAndGroup() {
        UUID groupId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        orgNodes.add(new OrgNodeLookup.OrgNode(
                parentId, null, "招商部", DeptType.DEPARTMENT, false));
        orgNodes.add(new OrgNodeLookup.OrgNode(
                groupId, parentId, "A 组", DeptType.RECRUITER_GROUP, false));

        SplitAssignment result = policy.splitAssignment(groupId);
        assertThat(result.parentDeptId()).isEqualTo(parentId);
        assertThat(result.groupId()).isEqualTo(groupId);
        assertThat(result.parentDeptName()).isEqualTo("招商部");
        assertThat(result.groupName()).isEqualTo("A 组");
        assertThat(result.groupType()).isEqualTo("recruiter_group");
    }

    @Test
    void splitAssignment_deptType_shouldReturnOnlyParent() {
        UUID deptId = UUID.randomUUID();
        orgNodes.add(new OrgNodeLookup.OrgNode(
                deptId, null, "渠道部", DeptType.DEPARTMENT, false));

        SplitAssignment result = policy.splitAssignment(deptId);
        assertThat(result.parentDeptId()).isEqualTo(deptId);
        assertThat(result.groupId()).isNull();
        assertThat(result.groupName()).isNull();
    }

    @Test
    void splitAssignment_deletedDept_shouldReturnAllNull() {
        UUID deptId = UUID.randomUUID();
        orgNodes.add(new OrgNodeLookup.OrgNode(
                deptId, null, "渠道部", DeptType.DEPARTMENT, true));

        SplitAssignment result = policy.splitAssignment(deptId);
        assertThat(result.parentDeptId()).isNull();
        assertThat(result.groupId()).isNull();
    }

    @Test
    void source_shouldNotDependOnPersistenceMapperOrEntity() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/policy/OrgAssignmentPolicy.java"));

        assertThat(source).doesNotContain("mapper.SysDeptMapper");
        assertThat(source).doesNotContain("entity.SysDept");
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
