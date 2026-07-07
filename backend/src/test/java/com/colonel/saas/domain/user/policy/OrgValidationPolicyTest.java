package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.port.OrgDeletionConstraintLookup;
import com.colonel.saas.domain.user.port.OrgLeaderCandidateLookup;
import com.colonel.saas.domain.user.port.OrgLeaderCandidateLookup.LeaderCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrgValidationPolicy 单测（DDD-USER-MIGRATION-003）。
 *
 * <p>覆盖 validateGroupLeader + assertCanDeleteDept 两个方法。行为必须与 OrgStructureService
 * 旧实现完全一致（被 25 个 OrgStructureServiceTest 用例间接验证）。</p>
 */
class OrgValidationPolicyTest {

    private Map<UUID, LeaderCandidate> leaderCandidates;
    private Map<UUID, DeletionCounts> deletionCounts;

    private OrgValidationPolicy policy;

    @BeforeEach
    void setUp() {
        leaderCandidates = new HashMap<>();
        deletionCounts = new HashMap<>();
        OrgLeaderCandidateLookup leaderCandidateLookup =
                leaderUserId -> Optional.ofNullable(leaderCandidates.get(leaderUserId));
        OrgDeletionConstraintLookup deletionConstraintLookup = new MapBackedDeletionConstraintLookup(deletionCounts);
        policy = new OrgValidationPolicy(
                leaderCandidateLookup,
                deletionConstraintLookup,
                new CurrentUserPermissionPolicy());
    }

    // ===== validateGroupLeader =====

    @Test
    void validateGroupLeader_nullLeaderId_shouldReturnNull() {
        assertThat(policy.validateGroupLeader(null, DeptType.RECRUITER_GROUP)).isNull();
    }

    @Test
    void validateGroupLeader_userNotExists_shouldThrow() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> policy.validateGroupLeader(userId, DeptType.RECRUITER_GROUP))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateGroupLeader_deletedUser_shouldThrow() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> policy.validateGroupLeader(userId, DeptType.RECRUITER_GROUP))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateGroupLeader_adminForAnyGroup_shouldReturnRealName() {
        UUID userId = UUID.randomUUID();
        registerLeader(userId, "张管理", null, RoleCodes.ADMIN);

        String name = policy.validateGroupLeader(userId, DeptType.RECRUITER_GROUP);
        assertThat(name).isEqualTo("张管理");
    }

    @Test
    void validateGroupLeader_shouldUseUserPermissionPolicyRoleNormalization() {
        UUID userId = UUID.randomUUID();
        registerLeader(userId, "张管理", null, " ADMIN ");

        String name = policy.validateGroupLeader(userId, DeptType.RECRUITER_GROUP);
        assertThat(name).isEqualTo("张管理");
    }

    @Test
    void validateGroupLeader_channelLeaderForChannelGroup_shouldReturnRealName() {
        UUID userId = UUID.randomUUID();
        registerLeader(userId, "李组长", null, RoleCodes.CHANNEL_LEADER);

        String name = policy.validateGroupLeader(userId, DeptType.CHANNEL_GROUP);
        assertThat(name).isEqualTo("李组长");
    }

    @Test
    void validateGroupLeader_recruiterGroupWithChannelRole_shouldThrow() {
        UUID userId = UUID.randomUUID();
        registerLeader(userId, null, null, RoleCodes.CHANNEL_LEADER);

        assertThatThrownBy(() -> policy.validateGroupLeader(userId, DeptType.RECRUITER_GROUP))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateGroupLeader_fallbackToUsername_whenNoRealName() {
        UUID userId = UUID.randomUUID();
        registerLeader(userId, null, "zhangsan", RoleCodes.ADMIN);

        String name = policy.validateGroupLeader(userId, DeptType.RECRUITER_GROUP);
        assertThat(name).isEqualTo("zhangsan");
    }

    @Test
    void validateGroupLeader_fallbackToUserId_whenNoName() {
        UUID userId = UUID.randomUUID();
        registerLeader(userId, null, null, RoleCodes.ADMIN);

        String name = policy.validateGroupLeader(userId, DeptType.RECRUITER_GROUP);
        assertThat(name).isEqualTo(userId.toString());
    }

    @Test
    void validateGroupLeader_opsStaffForOpsGroup_shouldReturnRealName() {
        UUID userId = UUID.randomUUID();
        registerLeader(userId, "王运营", null, RoleCodes.OPS_STAFF);

        String name = policy.validateGroupLeader(userId, DeptType.OPS_GROUP);
        assertThat(name).isEqualTo("王运营");
    }

    @Test
    void validateGroupLeader_departmentType_shouldAcceptMultipleLeaderRoles() {
        UUID userId = UUID.randomUUID();
        registerLeader(userId, "赵部门", null, RoleCodes.BIZ_LEADER);

        String name = policy.validateGroupLeader(userId, DeptType.DEPARTMENT);
        assertThat(name).isEqualTo("赵部门");
    }

    // ===== assertCanDeleteDept =====

    @Test
    void assertCanDeleteDept_withUsers_shouldThrow() {
        UUID deptId = UUID.randomUUID();
        deletionCounts.put(deptId, new DeletionCounts(3L, 0L));

        assertThatThrownBy(() -> policy.assertCanDeleteDept(deptId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assertCanDeleteDept_withChildGroups_shouldThrow() {
        UUID deptId = UUID.randomUUID();
        deletionCounts.put(deptId, new DeletionCounts(0L, 2L));

        assertThatThrownBy(() -> policy.assertCanDeleteDept(deptId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assertCanDeleteDept_clean_shouldNotThrow() {
        UUID deptId = UUID.randomUUID();
        deletionCounts.put(deptId, new DeletionCounts(0L, 0L));

        policy.assertCanDeleteDept(deptId);
    }

    @Test
    void policy_shouldDependOnUserDomainPortsNotPersistenceMappers() throws IOException {
        String source = Files.readString(sourcePath());

        assertThat(source).contains("OrgLeaderCandidateLookup");
        assertThat(source).contains("OrgDeletionConstraintLookup");
        assertThat(source).contains("CurrentUserPermissionPolicy");
        assertThat(source).contains("currentUserPermissionPolicy.hasAnyRole");
        assertThat(source).doesNotContain("roleCodes.stream().noneMatch");
        assertThat(source).doesNotContain("com.colonel.saas.mapper.");
    }

    private Path sourcePath() {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/domain/user/policy/OrgValidationPolicy.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/domain/user/policy/OrgValidationPolicy.java");
        }
        return sourcePath;
    }

    private void registerLeader(UUID userId, String realName, String username, String... roleCodes) {
        leaderCandidates.put(userId, new LeaderCandidate(
                userId,
                realName,
                username,
                Set.of(roleCodes)));
    }

    private record DeletionCounts(long directUsers, long childGroups) {
        private static final DeletionCounts EMPTY = new DeletionCounts(0L, 0L);
    }

    private static class MapBackedDeletionConstraintLookup implements OrgDeletionConstraintLookup {

        private final Map<UUID, DeletionCounts> deletionCounts;

        private MapBackedDeletionConstraintLookup(Map<UUID, DeletionCounts> deletionCounts) {
            this.deletionCounts = deletionCounts;
        }

        @Override
        public long countDirectUsers(UUID deptId) {
            return deletionCounts.getOrDefault(deptId, DeletionCounts.EMPTY).directUsers();
        }

        @Override
        public long countChildGroups(UUID deptId) {
            return deletionCounts.getOrDefault(deptId, DeletionCounts.EMPTY).childGroups();
        }
    }
}
