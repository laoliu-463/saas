package com.colonel.saas.domain.user.facade;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.testsupport.DockerAvailable;
import com.colonel.saas.testsupport.TestDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-USER-001：UserDomainFacade 只读门面集成测试。
 */
@DockerAvailable
class LegacyUserDomainFacadeTest extends BaseIntegrationTest {

    @Autowired
    private UserDomainFacade userDomainFacade;

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID channelDeptId;
    private UUID channelLeaderId;
    private UUID channelStaffId;
    private UUID bizLeaderId;
    private UUID adminId;

    @BeforeEach
    void seedRolesAndUsers() {
        jdbcTemplate.update("INSERT INTO sys_dept (id, dept_code, dept_name, status, deleted) VALUES (?, ?, ?, 1, 0) ON CONFLICT (dept_code) DO NOTHING",
                UUID.fromString("22222222-2222-2222-2222-222222222222"), "channel", "渠道部");
        jdbcTemplate.update("INSERT INTO sys_dept (id, dept_code, dept_name, status, deleted) VALUES (?, ?, ?, 1, 0) ON CONFLICT (dept_code) DO NOTHING",
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "biz", "招商部");

        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888801"), "channel_leader", "渠道组长", 2);
        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888802"), "channel_staff", "渠道专员", 1);
        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888803"), "biz_leader", "招商组长", 2);
        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888804"), "biz_staff", "招商专员", 1);
        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888800"), RoleCodes.ADMIN, "管理员", 3);

        channelDeptId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        channelLeaderId = UUID.nameUUIDFromBytes("channel_leader".getBytes());
        channelStaffId = UUID.nameUUIDFromBytes("channel_staff".getBytes());
        bizLeaderId = UUID.nameUUIDFromBytes("biz_leader".getBytes());
        adminId = UUID.nameUUIDFromBytes("admin".getBytes());

        insertUser(channelLeaderId, "channel_leader", "渠道组长", "ch_lead", channelDeptId);
        insertUser(channelStaffId, "channel_staff", "渠道组员", "ch_staff", channelDeptId);
        insertUser(bizLeaderId, "biz_leader", "招商组长", "biz_lead",
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        insertUser(adminId, RoleCodes.ADMIN, "系统管理员", "admin",
                UUID.fromString("00000000-0000-0000-0000-000000000001"));

        bindUserRole(channelLeaderId, "channel_leader");
        bindUserRole(channelStaffId, "channel_staff");
        bindUserRole(bizLeaderId, "biz_leader");
        bindUserRole(adminId, RoleCodes.ADMIN);
    }

    @Test
    void resolveDataScope_adminShouldBeAll() {
        UserDataScopeResponse scope = userDomainFacade.resolveDataScope(adminId);

        assertThat(scope.scope()).isEqualTo("all");
        assertThat(scope.code()).isEqualTo(DataScope.ALL.getCode());
        assertThat(scope.userIds()).isEmpty();
    }

    @Test
    void resolveDataScope_channelLeaderShouldBeGroup() {
        UserDataScopeResponse scope = userDomainFacade.resolveDataScope(channelLeaderId);

        assertThat(scope.scope()).isEqualTo("group");
        assertThat(scope.code()).isEqualTo(DataScope.DEPT.getCode());
        assertThat(scope.userIds()).contains(channelLeaderId, channelStaffId);
    }

    @Test
    void resolveDataScope_channelStaffShouldBeSelf() {
        UserDataScopeResponse scope = userDomainFacade.resolveDataScope(channelStaffId);

        assertThat(scope.scope()).isEqualTo("self");
        assertThat(scope.code()).isEqualTo(DataScope.PERSONAL.getCode());
        assertThat(scope.userIds()).containsExactly(channelStaffId);
    }

    @Test
    void listChannelsAndRecruitersShouldReturnRoleFilteredOptions() {
        testDataService.seedAll(false);

        List<UserOptionResponse> channels = userDomainFacade.listChannels(null);
        assertThat(channels).isNotEmpty();
        assertThat(channels).allMatch(o -> o.roleCodes().stream().anyMatch(code ->
                RoleCodes.CHANNEL_LEADER.equals(code) || RoleCodes.CHANNEL_STAFF.equals(code)));

        List<UserOptionResponse> recruiters = userDomainFacade.listRecruiters("招商");
        assertThat(recruiters).isNotEmpty();
        assertThat(recruiters).allMatch(o -> o.roleCodes().stream().anyMatch(code ->
                RoleCodes.BIZ_LEADER.equals(code) || RoleCodes.BIZ_STAFF.equals(code)));
    }

    @Test
    void listGroupMembers_channelLeaderSeesDeptMembers() {
        List<UserOptionResponse> members = userDomainFacade.listGroupMembers(channelDeptId, channelLeaderId);

        assertThat(members).extracting(UserOptionResponse::id)
                .contains(channelLeaderId, channelStaffId);
    }

    @Test
    void listDepartmentsShouldReturnActiveDepts() {
        List<com.colonel.saas.domain.user.facade.dto.DepartmentOption> depts = userDomainFacade.listDepartments();

        assertThat(depts).isNotEmpty();
        assertThat(depts).anyMatch(d -> "channel".equals(d.deptCode()));
    }

    @Test
    void getUsernameShouldReturnLoginAccount() {
        assertThat(userDomainFacade.getUsername(channelLeaderId)).isEqualTo("channel_leader");
        assertThat(userDomainFacade.getUserName(channelLeaderId)).isEqualTo("渠道组长");
    }

    @Test
    void loadUserDisplayLabelsShouldReturnDisplayTextWithoutFullUserDto() {
        Map<UUID, String> labels = userDomainFacade.loadUserDisplayLabelsByIds(List.of(channelLeaderId));

        assertThat(labels).containsEntry(channelLeaderId, "渠道组长 (channel_leader)");
    }

    @Test
    void loadUserDisplayNamesShouldPreferRealNameThenUsername() {
        UUID usernameOnlyId = UUID.nameUUIDFromBytes("username_only_display_name".getBytes());
        insertUser(usernameOnlyId, "username_only_display_name", null, "username_only", channelDeptId);

        Map<UUID, String> names = userDomainFacade.loadUserDisplayNamesByIds(List.of(channelLeaderId, usernameOnlyId));

        assertThat(names)
                .containsEntry(channelLeaderId, "渠道组长")
                .containsEntry(usernameOnlyId, "username_only_display_name");
    }

    @Test
    void loadUserOwnershipReferencesShouldReturnDeptIdWithoutFullUserDto() {
        Map<UUID, UserOwnershipReference> references =
                userDomainFacade.loadUserOwnershipReferencesByIds(List.of(channelLeaderId));

        assertThat(references).containsKey(channelLeaderId);
        assertThat(references.get(channelLeaderId).userId()).isEqualTo(channelLeaderId);
        assertThat(references.get(channelLeaderId).deptId()).isEqualTo(channelDeptId);
    }

    @Test
    void hasPermission_adminAlwaysAllowed() {
        assertThat(userDomainFacade.hasPermission(adminId, "order", "export")).isTrue();
    }

    @Test
    void hasPermission_channelStaffDeniedWithoutConfiguredOps() {
        assertThat(userDomainFacade.hasPermission(channelStaffId, "nonexistent_resource", "audit")).isFalse();
    }

    private void insertRole(UUID id, String roleCode, String roleName, int dataScope) {
        jdbcTemplate.update("""
                        INSERT INTO sys_role (id, role_code, role_name, data_scope, status, permissions)
                        VALUES (?, ?, ?, ?, 1, ?::jsonb) ON CONFLICT (role_code) DO NOTHING
                        """,
                id, roleCode, roleName, dataScope, "{\"menus\":[\"talent_crm\"]}");
    }

    private void insertUser(UUID id, String username, String realName, String channelCode, UUID deptId) {
        jdbcTemplate.update("""
                        INSERT INTO sys_user (id, username, password, real_name, channel_code, dept_id, status, deleted)
                        VALUES (?, ?, ?, ?, ?, ?, 1, 0) ON CONFLICT (username) DO NOTHING
                        """,
                id, username, "password", realName, channelCode, deptId);
    }

    private void bindUserRole(UUID userId, String roleCode) {
        jdbcTemplate.update("""
                        INSERT INTO sys_user_role (user_id, role_id)
                        SELECT ?, id FROM sys_role WHERE role_code = ? ON CONFLICT DO NOTHING
                        """,
                userId, roleCode);
    }
}
