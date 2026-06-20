package com.colonel.saas.domain.user.application;

import com.colonel.saas.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.domain.user.port.OrgUnitDirectoryLookup;
import com.colonel.saas.domain.user.port.OrgUnitDirectoryLookup.OrgUnitEntry;
import com.colonel.saas.domain.user.port.OrgUnitMemberLookup;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrgUnitDirectoryApplicationServiceTest {

    @Test
    void applicationServiceShouldDependOnDirectoryPortOnly() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/application/OrgUnitDirectoryApplicationService.java"));

        assertThat(source).contains("OrgUnitDirectoryLookup");
        assertThat(source).doesNotContain("com.colonel.saas.mapper.");
        assertThat(source).doesNotContain("com.colonel.saas.entity.");
        assertThat(source).doesNotContain("com.colonel.saas.domain.user.infrastructure.");
    }

    @Test
    void findAll_shouldMapOrgUnitEntriesToDeptVos() {
        UUID deptId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        OrgUnitDirectoryApplicationService service = new OrgUnitDirectoryApplicationService(new FakeDirectoryLookup(List.of(
                new OrgUnitEntry(
                        deptId,
                        null,
                        "BIZ",
                        "招商组",
                        DeptType.RECRUITER_GROUP,
                        leaderUserId,
                        "张三",
                        "13800000000",
                        "biz@example.com",
                        10,
                        1,
                        "remark"))), new FakeMemberLookup());

        var result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(deptId);
        assertThat(result.get(0).getLeaderUserId()).isEqualTo(leaderUserId);
        assertThat(result.get(0).getDeptCode()).isEqualTo("BIZ");
        assertThat(result.get(0).getDeptName()).isEqualTo("招商组");
        assertThat(result.get(0).getDeptType()).isEqualTo(DeptType.RECRUITER_GROUP);
        assertThat(result.get(0).getPhone()).isEqualTo("13800000000");
        assertThat(result.get(0).getEmail()).isEqualTo("biz@example.com");
        assertThat(result.get(0).getSortOrder()).isEqualTo(10);
        assertThat(result.get(0).getStatus()).isEqualTo(1);
        assertThat(result.get(0).getRemark()).isEqualTo("remark");
    }

    @Test
    void findTree_shouldAttachChildrenToExistingParents() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        OrgUnitDirectoryApplicationService service = new OrgUnitDirectoryApplicationService(new FakeDirectoryLookup(List.of(
                new OrgUnitEntry(
                        rootId,
                        null,
                        "BIZ",
                        "招商组",
                        DeptType.DEPARTMENT,
                        null,
                        null,
                        null,
                        null,
                        10,
                        1,
                        null),
                new OrgUnitEntry(
                        childId,
                        rootId,
                        "BIZ_EAST",
                        "招商一组",
                        DeptType.RECRUITER_GROUP,
                        null,
                        null,
                        null,
                        null,
                        20,
                        1,
                        null))), new FakeMemberLookup());

        var tree = service.findTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getId()).isEqualTo(rootId);
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getId()).isEqualTo(childId);
    }

    @Test
    void getById_missingOrgUnit_shouldThrowNotFound() {
        OrgUnitDirectoryApplicationService service = new OrgUnitDirectoryApplicationService(
                new FakeDirectoryLookup(List.of()), new FakeMemberLookup());

        assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门不存在");
    }

    @Test
    void findGroupsByParent_shouldReturnOnlyGroupChildrenMatchingType() {
        UUID parentId = UUID.randomUUID();
        UUID recruiterGroupId = UUID.randomUUID();
        UUID channelGroupId = UUID.randomUUID();
        OrgUnitDirectoryApplicationService service = new OrgUnitDirectoryApplicationService(new FakeDirectoryLookup(List.of(
                new OrgUnitEntry(
                        parentId,
                        null,
                        "BIZ",
                        "招商部",
                        DeptType.DEPARTMENT,
                        null,
                        null,
                        null,
                        null,
                        10,
                        1,
                        null),
                new OrgUnitEntry(
                        recruiterGroupId,
                        parentId,
                        "BIZ_EAST",
                        "招商一组",
                        DeptType.RECRUITER_GROUP,
                        null,
                        null,
                        null,
                        null,
                        20,
                        1,
                        null),
                new OrgUnitEntry(
                        channelGroupId,
                        parentId,
                        "CHANNEL_EAST",
                        "渠道一组",
                        DeptType.CHANNEL_GROUP,
                        null,
                        null,
                        null,
                        null,
                        30,
                        1,
                        null))), new FakeMemberLookup());

        var groups = service.findGroupsByParent(parentId, DeptType.RECRUITER_GROUP);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getId()).isEqualTo(recruiterGroupId);
        assertThat(groups.get(0).getDeptType()).isEqualTo(DeptType.RECRUITER_GROUP);
    }

    @Test
    void getStats_shouldKeepDirectoryStatsSemantics() {
        UUID deptId = UUID.randomUUID();
        FakeDirectoryLookup lookup = new FakeDirectoryLookup(List.of(
                new OrgUnitEntry(
                        deptId,
                        null,
                        "BIZ",
                        "招商部",
                        DeptType.DEPARTMENT,
                        null,
                        null,
                        null,
                        null,
                        10,
                        1,
                        null)));
        lookup.memberCount = 9;
        lookup.recruiterGroupCount = 3;
        lookup.channelGroupCount = 2;
        OrgUnitDirectoryApplicationService service = new OrgUnitDirectoryApplicationService(lookup, new FakeMemberLookup());

        var stats = service.getStats(deptId);

        assertThat(stats.getDeptId()).isEqualTo(deptId);
        assertThat(stats.getMemberCount()).isEqualTo(9);
        assertThat(stats.getRecruiterGroupCount()).isEqualTo(3);
        assertThat(stats.getChannelGroupCount()).isEqualTo(2);
    }

    @Test
    void findMembers_shouldValidateOrgUnitBeforeDelegatingMemberLookup() {
        UUID deptId = UUID.randomUUID();
        DeptMemberPageRequest request = new DeptMemberPageRequest(2, 10, "张", 1, null, null, null);
        FakeMemberLookup memberLookup = new FakeMemberLookup();
        Page<SysUserVO> page = new Page<>(2, 10);
        memberLookup.page = page;
        OrgUnitDirectoryApplicationService service = new OrgUnitDirectoryApplicationService(
                new FakeDirectoryLookup(List.of(new OrgUnitEntry(
                        deptId,
                        null,
                        "CHANNEL",
                        "渠道部",
                        DeptType.DEPARTMENT,
                        null,
                        null,
                        null,
                        null,
                        10,
                        1,
                        null))),
                memberLookup);

        IPage<SysUserVO> result = service.findMembers(deptId, request);

        assertThat(result).isSameAs(page);
        assertThat(memberLookup.lastOrgUnitId).isEqualTo(deptId);
        assertThat(memberLookup.lastRequest).isSameAs(request);
    }

    @Test
    void findMembers_missingOrgUnit_shouldThrowBeforeMemberLookup() {
        FakeMemberLookup memberLookup = new FakeMemberLookup();
        OrgUnitDirectoryApplicationService service = new OrgUnitDirectoryApplicationService(
                new FakeDirectoryLookup(List.of()), memberLookup);

        assertThatThrownBy(() -> service.findMembers(UUID.randomUUID(),
                new DeptMemberPageRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门不存在");
        assertThat(memberLookup.lastOrgUnitId).isNull();
    }

    private static final class FakeDirectoryLookup implements OrgUnitDirectoryLookup {
        private final List<OrgUnitEntry> entries;
        private long memberCount;
        private long recruiterGroupCount;
        private long channelGroupCount;

        private FakeDirectoryLookup(List<OrgUnitEntry> entries) {
            this.entries = entries;
        }

        @Override
        public List<OrgUnitEntry> listActive() {
            return entries;
        }

        @Override
        public Optional<OrgUnitEntry> findActiveById(UUID id) {
            return entries.stream()
                    .filter(entry -> entry.id().equals(id))
                    .findFirst();
        }

        @Override
        public List<OrgUnitEntry> findChildren(UUID parentId) {
            return entries.stream()
                    .filter(entry -> parentId.equals(entry.parentId()))
                    .toList();
        }

        @Override
        public long countMembersUnderOrgUnit(UUID orgUnitId) {
            return memberCount;
        }

        @Override
        public long countChildGroupsByType(UUID parentId, String deptType) {
            if (DeptType.RECRUITER_GROUP.equals(deptType)) {
                return recruiterGroupCount;
            }
            if (DeptType.CHANNEL_GROUP.equals(deptType)) {
                return channelGroupCount;
            }
            return 0;
        }
    }

    private static final class FakeMemberLookup implements OrgUnitMemberLookup {
        private IPage<SysUserVO> page = new Page<>();
        private UUID lastOrgUnitId;
        private DeptMemberPageRequest lastRequest;

        @Override
        public IPage<SysUserVO> findMembers(UUID orgUnitId, DeptMemberPageRequest request) {
            this.lastOrgUnitId = orgUnitId;
            this.lastRequest = request;
            return page;
        }
    }
}
