package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.OrgValidationPolicy;
import com.colonel.saas.domain.user.port.OrgDeletionConstraintLookup;
import com.colonel.saas.domain.user.port.OrgDepartmentRepository;
import com.colonel.saas.domain.user.port.OrgDepartmentRepository.DepartmentRecord;
import com.colonel.saas.domain.user.port.OrgLeaderCandidateLookup;
import com.colonel.saas.domain.user.port.OrgLeaderCandidateLookup.LeaderCandidate;
import com.colonel.saas.service.OperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class OrgUnitWriteApplicationServiceTest {

    @Mock
    private OperationLogService operationLogService;

    private FakeDepartmentRepository departmentRepository;
    private FakeLeaderCandidateLookup leaderCandidateLookup;
    private FakeDeletionConstraintLookup deletionConstraintLookup;
    private OrgUnitWriteApplicationService service;

    @BeforeEach
    void setUp() {
        departmentRepository = new FakeDepartmentRepository();
        leaderCandidateLookup = new FakeLeaderCandidateLookup();
        deletionConstraintLookup = new FakeDeletionConstraintLookup();
        OrgValidationPolicy validationPolicy = new OrgValidationPolicy(
                leaderCandidateLookup,
                deletionConstraintLookup);
        service = new OrgUnitWriteApplicationService(
                departmentRepository,
                validationPolicy,
                operationLogService);
    }

    @Test
    void applicationServiceShouldUsePortsOnly() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/application/OrgUnitWriteApplicationService.java"));

        assertThat(source).contains("OrgDepartmentRepository");
        assertThat(source).contains("OrgValidationPolicy");
        assertThat(source).doesNotContain("com.colonel.saas.mapper.");
        assertThat(source).doesNotContain("com.colonel.saas.entity.");
        assertThat(source).doesNotContain("com.colonel.saas.domain.user.infrastructure.");
    }

    @Test
    void create_shouldValidateLeaderRoleAndPreserveTrueRouteSemantics() {
        UUID leaderId = UUID.randomUUID();
        leaderCandidateLookup.candidates.put(leaderId, new LeaderCandidate(
                leaderId,
                "招商组长",
                "biz_leader",
                Set.of(RoleCodes.BIZ_LEADER)));
        SysDeptCreateRequest request = new SysDeptCreateRequest(
                null,
                " biz_east ",
                " 招商一组 ",
                "文本负责人",
                " 13800000000 ",
                " biz@example.com ",
                null,
                null,
                " remark ",
                DeptType.RECRUITER_GROUP,
                leaderId);

        var result = service.create(request, UUID.randomUUID());

        assertThat(result.getDeptCode()).isEqualTo("biz_east");
        assertThat(result.getDeptName()).isEqualTo("招商一组");
        assertThat(result.getLeader()).isEqualTo("招商组长");
        assertThat(result.getPhone()).isEqualTo(" 13800000000 ");
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(departmentRepository.inserted).isNotNull();
    }

    @Test
    void update_shouldAllowChannelLeaderOnlyForOwnOrgUnitAndKeepDeptCodeUpdate() {
        UUID deptId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        departmentRepository.records.put(deptId, record(
                deptId,
                "OLD_CODE",
                "旧组",
                DeptType.CHANNEL_GROUP,
                currentUserId));
        SysDeptUpdateRequest request = new SysDeptUpdateRequest(
                null,
                " NEW_CODE ",
                " 新渠道组 ",
                "手填负责人",
                null,
                null,
                null,
                null,
                null,
                DeptType.CHANNEL_GROUP,
                null);

        var result = service.update(deptId, request, currentUserId, List.of(RoleCodes.CHANNEL_LEADER));

        assertThat(result.getDeptCode()).isEqualTo("NEW_CODE");
        assertThat(result.getDeptName()).isEqualTo("新渠道组");
        assertThat(result.getLeader()).isEqualTo("手填负责人");
        assertThat(result.getSortOrder()).isEqualTo(0);
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(departmentRepository.updated.deptCode()).isEqualTo("NEW_CODE");
    }

    @Test
    void update_shouldRejectChannelLeaderForOtherOrgUnit() {
        UUID deptId = UUID.randomUUID();
        departmentRepository.records.put(deptId, record(
                deptId,
                "CHANNEL",
                "渠道组",
                DeptType.CHANNEL_GROUP,
                UUID.randomUUID()));
        SysDeptUpdateRequest request = new SysDeptUpdateRequest(
                null,
                "CHANNEL",
                "渠道组",
                null,
                null,
                null,
                0,
                1,
                null,
                DeptType.CHANNEL_GROUP,
                null);

        assertThatThrownBy(() -> service.update(
                deptId,
                request,
                UUID.randomUUID(),
                List.of(RoleCodes.CHANNEL_LEADER)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权修改该部门");
    }

    @Test
    void delete_shouldCheckPermissionAndDeletionConstraintsBeforeSoftDelete() {
        UUID deptId = UUID.randomUUID();
        departmentRepository.records.put(deptId, record(
                deptId,
                "CHANNEL",
                "渠道组",
                DeptType.CHANNEL_GROUP,
                null));

        service.delete(deptId, UUID.randomUUID(), List.of(RoleCodes.ADMIN));

        assertThat(deletionConstraintLookup.checkedDeptId).isEqualTo(deptId);
        assertThat(departmentRepository.softDeletedId).isEqualTo(deptId);
    }

    @Test
    void delete_softDeleteRace_shouldReturnNotFound() {
        UUID deptId = UUID.randomUUID();
        departmentRepository.records.put(deptId, record(
                deptId,
                "CHANNEL",
                "渠道组",
                DeptType.CHANNEL_GROUP,
                null));
        departmentRepository.softDeleteResult = 0;

        assertThatThrownBy(() -> service.delete(deptId, UUID.randomUUID(), List.of(RoleCodes.ADMIN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门不存在或已删除");
    }

    private static DepartmentRecord record(
            UUID id,
            String deptCode,
            String deptName,
            String deptType,
            UUID leaderUserId) {
        return new DepartmentRecord(
                id,
                null,
                deptCode,
                deptName,
                deptType,
                leaderUserId,
                null,
                null,
                null,
                20,
                1,
                null,
                0);
    }

    private static final class FakeDepartmentRepository implements OrgDepartmentRepository {
        private final Map<UUID, DepartmentRecord> records = new HashMap<>();
        private DepartmentRecord inserted;
        private DepartmentRecord updated;
        private UUID softDeletedId;
        private int softDeleteResult = 1;

        @Override
        public List<DepartmentRecord> listActive() {
            return records.values().stream().filter(record -> !record.isDeleted()).toList();
        }

        @Override
        public List<DepartmentRecord> listByDeptType(String deptType) {
            return listActive().stream().filter(record -> deptType.equals(record.deptType())).toList();
        }

        @Override
        public List<DepartmentRecord> listNonDeleted() {
            return listActive();
        }

        @Override
        public Optional<DepartmentRecord> findById(UUID id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public Optional<DepartmentRecord> findByDeptCode(String deptCode) {
            return records.values().stream()
                    .filter(record -> !record.isDeleted())
                    .filter(record -> deptCode.equals(record.deptCode()))
                    .findFirst();
        }

        @Override
        public void insert(DepartmentRecord department) {
            inserted = department;
            records.put(department.id(), department);
        }

        @Override
        public void update(DepartmentRecord department) {
            updated = department;
            records.put(department.id(), department);
        }

        @Override
        public long countUsersByDeptId(UUID deptId) {
            return 0;
        }

        @Override
        public long countChildrenByParentId(UUID parentId) {
            return 0;
        }

        @Override
        public int softDeleteById(UUID id) {
            softDeletedId = id;
            return softDeleteResult;
        }
    }

    private static final class FakeLeaderCandidateLookup implements OrgLeaderCandidateLookup {
        private final Map<UUID, LeaderCandidate> candidates = new HashMap<>();

        @Override
        public Optional<LeaderCandidate> findActiveLeaderCandidate(UUID leaderUserId) {
            return Optional.ofNullable(candidates.get(leaderUserId));
        }
    }

    private static final class FakeDeletionConstraintLookup implements OrgDeletionConstraintLookup {
        private UUID checkedDeptId;

        @Override
        public long countDirectUsers(UUID deptId) {
            checkedDeptId = deptId;
            return 0;
        }

        @Override
        public long countChildGroups(UUID deptId) {
            checkedDeptId = deptId;
            return 0;
        }
    }
}
