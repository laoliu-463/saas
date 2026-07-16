package com.colonel.saas.domain.performance.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.entity.PerformanceRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PerformanceAccessScopeTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final CurrentUserPermissionChecker currentUserPermissionChecker =
            new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy());
    private final DataScopeResolver dataScopeResolver = new DataScopeResolver(new DataScopePolicy());

    @Test
    void canExport_shouldAllowLeadersAndAdmin() {
        assertThat(PerformanceAccessScope.canExport(null, currentUserPermissionChecker)).isFalse();
        assertThat(PerformanceAccessScope.canExport(
                context(List.of(" ADMIN "), DataScope.PERSONAL),
                currentUserPermissionChecker)).isTrue();
        assertThat(PerformanceAccessScope.canExport(
                context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT),
                currentUserPermissionChecker)).isTrue();
        assertThat(PerformanceAccessScope.canExport(
                context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT),
                currentUserPermissionChecker)).isTrue();
        assertThat(PerformanceAccessScope.canExport(
                context(List.of(RoleCodes.CHANNEL_LEADER), DataScope.DEPT),
                currentUserPermissionChecker)).isTrue();
        assertThat(PerformanceAccessScope.canExport(
                context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL),
                currentUserPermissionChecker)).isFalse();
    }

    @Test
    void canRecalculateMonth_shouldAllowOnlyAdmin() {
        assertThat(PerformanceAccessScope.canRecalculateMonth(null, currentUserPermissionChecker)).isFalse();
        assertThat(PerformanceAccessScope.canRecalculateMonth(
                context(List.of(RoleCodes.ADMIN), DataScope.PERSONAL),
                currentUserPermissionChecker)).isTrue();
        assertThat(PerformanceAccessScope.canRecalculateMonth(
                context(List.of(RoleCodes.OPS_STAFF), DataScope.ALL),
                currentUserPermissionChecker)).isFalse();
    }

    @Test
    void assertFilterAllowed_shouldRejectMissingContextAndCrossStaffFilters() {
        assertThatThrownBy(() -> PerformanceAccessScope.assertFilterAllowed(
                USER, null, null, currentUserPermissionChecker))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问业绩数据");
        assertThatThrownBy(() -> PerformanceAccessScope.assertFilterAllowed(OTHER, null,
                context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL),
                currentUserPermissionChecker))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权按该渠道筛选业绩");
        assertThatThrownBy(() -> PerformanceAccessScope.assertFilterAllowed(null, OTHER,
                context(List.of(RoleCodes.BIZ_STAFF), DataScope.PERSONAL),
                currentUserPermissionChecker))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权按该招商筛选业绩");
        assertThatThrownBy(() -> PerformanceAccessScope.assertFilterAllowed(OTHER, null,
                context(List.of(RoleCodes.CHANNEL_STAFF, RoleCodes.BIZ_STAFF), DataScope.PERSONAL),
                currentUserPermissionChecker))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权按该渠道筛选业绩");
    }

    @Test
    void assertFilterAllowed_shouldAllowAdminLikeAndOwnStaffFilters() {
        PerformanceAccessScope.assertFilterAllowed(
                OTHER, OTHER, context(List.of(RoleCodes.OPS_STAFF), DataScope.PERSONAL), currentUserPermissionChecker);
        PerformanceAccessScope.assertFilterAllowed(
                OTHER, OTHER, context(List.of(), DataScope.ALL), currentUserPermissionChecker);
        PerformanceAccessScope.assertFilterAllowed(
                USER, null, context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL), currentUserPermissionChecker);
        PerformanceAccessScope.assertFilterAllowed(
                null, USER, context(List.of(RoleCodes.BIZ_STAFF), DataScope.PERSONAL), currentUserPermissionChecker);
        PerformanceAccessScope.assertFilterAllowed(
                OTHER, OTHER, context(List.of(RoleCodes.CHANNEL_LEADER), DataScope.DEPT), currentUserPermissionChecker);
        PerformanceAccessScope.assertFilterAllowed(
                OTHER, OTHER, context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT), currentUserPermissionChecker);
    }

    @Test
    void canAccessRecord_shouldRejectMissingRecordContextAndUser() {
        assertThat(PerformanceAccessScope.canAccessRecord(
                null,
                context(List.of(RoleCodes.ADMIN), DataScope.ALL),
                currentUserPermissionChecker)).isFalse();
        assertThat(PerformanceAccessScope.canAccessRecord(
                record(USER, USER),
                null,
                currentUserPermissionChecker)).isFalse();
        PerformanceAccessContext noUser = PerformanceAccessContext.of(null, DEPT, DataScope.PERSONAL, List.of());
        assertThat(PerformanceAccessScope.canAccessRecord(
                record(USER, USER),
                noUser,
                currentUserPermissionChecker)).isFalse();
    }

    @Test
    void canAccessRecord_shouldAllowAdminLike() {
        assertThat(PerformanceAccessScope.canAccessRecord(
                record(OTHER, OTHER),
                context(List.of(RoleCodes.ADMIN), DataScope.PERSONAL),
                currentUserPermissionChecker))
                .isTrue();
        assertThat(PerformanceAccessScope.canAccessRecord(
                record(OTHER, OTHER),
                context(List.of(), DataScope.ALL),
                currentUserPermissionChecker))
                .isTrue();
    }

    @Test
    void canAccessRecord_shouldRestrictChannelStaff() {
        PerformanceRecord record = record(OTHER, USER);
        assertThat(PerformanceAccessScope.canAccessRecord(
                record,
                context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL),
                currentUserPermissionChecker))
                .isFalse();
        record.setFinalChannelUserId(USER);
        assertThat(PerformanceAccessScope.canAccessRecord(
                record,
                context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL),
                currentUserPermissionChecker))
                .isTrue();
    }

    @Test
    void bizStaffWithPersonalScopeShouldOnlySeeOwnFinalRecruiter() {
        PerformanceRecord record = record(OTHER, USER);
        assertThat(PerformanceAccessScope.canAccessRecord(
                record,
                context(List.of(RoleCodes.BIZ_STAFF), DataScope.PERSONAL),
                currentUserPermissionChecker))
                .isTrue();
        record.setFinalRecruiterUserId(OTHER);
        assertThat(PerformanceAccessScope.canAccessRecord(
                record,
                context(List.of(RoleCodes.BIZ_STAFF), DataScope.PERSONAL),
                currentUserPermissionChecker))
                .isFalse();
    }

    @Test
    void dualStaffShouldUseUnionOfChannelAndRecruiterOwnership() {
        PerformanceRecord record = record(USER, OTHER);
        assertThat(PerformanceAccessScope.canAccessRecord(
                record,
                context(List.of(RoleCodes.CHANNEL_STAFF, RoleCodes.BIZ_STAFF), DataScope.PERSONAL),
                currentUserPermissionChecker)).isTrue();
        record.setFinalChannelUserId(OTHER);
        record.setFinalRecruiterUserId(USER);
        assertThat(PerformanceAccessScope.canAccessRecord(
                record,
                context(List.of(RoleCodes.CHANNEL_STAFF, RoleCodes.BIZ_STAFF), DataScope.PERSONAL),
                currentUserPermissionChecker)).isTrue();
    }

    @Test
    void canAccessRecord_shouldRestrictLeadersToMatchingDeptMemberPlaceholder() {
        assertThat(PerformanceAccessScope.canAccessRecord(record(USER, OTHER),
                context(List.of(RoleCodes.CHANNEL_LEADER), DataScope.DEPT),
                currentUserPermissionChecker)).isTrue();
        assertThat(PerformanceAccessScope.canAccessRecord(record(OTHER, USER),
                context(List.of(RoleCodes.CHANNEL_LEADER), DataScope.DEPT),
                currentUserPermissionChecker)).isFalse();
        assertThat(PerformanceAccessScope.canAccessRecord(record(OTHER, USER),
                context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT),
                currentUserPermissionChecker)).isTrue();
        assertThat(PerformanceAccessScope.canAccessRecord(record(USER, OTHER),
                context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT),
                currentUserPermissionChecker)).isFalse();
    }

    @Test
    void canAccessRecord_shouldUnionCompositeBusinessDimensions() {
        PerformanceAccessContext compositeLeaders = context(
                List.of(RoleCodes.CHANNEL_LEADER, RoleCodes.BIZ_LEADER),
                DataScope.DEPT);

        assertThat(PerformanceAccessScope.canAccessRecord(
                record(USER, OTHER), compositeLeaders, currentUserPermissionChecker)).isTrue();
        assertThat(PerformanceAccessScope.canAccessRecord(
                record(OTHER, USER), compositeLeaders, currentUserPermissionChecker)).isTrue();
        assertThat(PerformanceAccessScope.canAccessRecord(
                record(OTHER, OTHER), compositeLeaders, currentUserPermissionChecker)).isFalse();
    }

    @Test
    void canAccessRecord_shouldApplyPersonalFallbackAndAllowDeptFallback() {
        assertThat(PerformanceAccessScope.canAccessRecord(
                record(OTHER, USER),
                context(List.of(), DataScope.PERSONAL),
                currentUserPermissionChecker))
                .isTrue();
        assertThat(PerformanceAccessScope.canAccessRecord(
                record(OTHER, OTHER),
                context(List.of(), DataScope.PERSONAL),
                currentUserPermissionChecker))
                .isFalse();
        assertThat(PerformanceAccessScope.canAccessRecord(
                record(OTHER, OTHER),
                context(List.of(), DataScope.DEPT),
                currentUserPermissionChecker))
                .isTrue();
    }

    @Test
    void appendScopeCondition_shouldIgnoreMissingWhereOrContextAndAdminLike() {
        ArrayList<Object> args = new ArrayList<>();
        PerformanceAccessScope.appendScopeCondition(
                null, args, context(List.of(), DataScope.PERSONAL), "pr", currentUserPermissionChecker);
        PerformanceAccessScope.appendScopeCondition(
                new StringBuilder("WHERE 1=1"), args, null, "pr", currentUserPermissionChecker);
        StringBuilder where = new StringBuilder("WHERE 1=1");
        PerformanceAccessScope.appendScopeCondition(
                where,
                args,
                context(List.of(RoleCodes.ADMIN), DataScope.PERSONAL),
                "pr",
                currentUserPermissionChecker);

        assertThat(args).isEmpty();
        assertThat(where.toString()).isEqualTo("WHERE 1=1");
    }

    @Test
    void appendScopeCondition_shouldAddChannelStaffFilter() {
        ScopeResult result = append(context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL), "pr");

        assertThat(result.where()).contains("pr.final_channel_user_id = ?");
        assertThat(result.args()).containsExactly(USER);
    }

    @Test
    void appendScopeCondition_shouldAddRecruiterStaffFilter() {
        ScopeResult result = append(context(List.of(RoleCodes.BIZ_STAFF), DataScope.PERSONAL), "perf");

        assertThat(result.where()).contains("perf.final_recruiter_user_id = ?");
        assertThat(result.args()).containsExactly(USER);
    }

    @Test
    void appendScopeCondition_shouldAddLeaderDeptSubquery() {
        ScopeResult channel = append(context(List.of(RoleCodes.CHANNEL_LEADER), DataScope.DEPT), " pr ");
        ScopeResult recruiter = append(context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT), "pr");

        assertThat(channel.where()).contains("pr.final_channel_dept_id = ?");
        assertThat(channel.args()).containsExactly(DEPT);
        assertThat(recruiter.where()).contains("pr.final_recruiter_dept_id = ?");
        assertThat(recruiter.args()).containsExactly(DEPT);
    }

    @Test
    void appendScopeCondition_shouldUnionCompositeRoleDimensions() {
        ScopeResult leaders = append(
                context(List.of(RoleCodes.CHANNEL_LEADER, RoleCodes.BIZ_LEADER), DataScope.DEPT),
                "pr");
        ScopeResult staff = append(
                context(List.of(RoleCodes.CHANNEL_STAFF, RoleCodes.BIZ_STAFF), DataScope.PERSONAL),
                "pr");

        assertThat(leaders.where())
                .contains("pr.final_channel_user_id IN")
                .contains("OR pr.final_recruiter_user_id IN");
        assertThat(leaders.args()).containsExactly(DEPT, DEPT);
        assertThat(staff.where())
                .contains("pr.final_channel_user_id = ?")
                .contains("OR pr.final_recruiter_user_id = ?");
        assertThat(staff.args()).containsExactly(USER, USER);
    }

    @Test
    void appendScopeCondition_shouldAddDeptOrPersonalFallback() {
        ScopeResult dept = append(context(List.of(), DataScope.DEPT), "pr");
        ScopeResult personal = append(context(List.of(), DataScope.PERSONAL), "");

        assertThat(dept.where())
                .contains("pr.final_channel_user_id IN")
                .contains("OR pr.final_recruiter_user_id IN");
        assertThat(dept.args()).containsExactly(DEPT, DEPT);
        assertThat(personal.where()).contains("pr.final_channel_user_id = ? OR pr.final_recruiter_user_id = ?");
        assertThat(personal.args()).containsExactly(USER, USER);
    }

    @Test
    void appendScopeConditionWithResolver_shouldPreserveDeptAndPersonalFallbackSql() {
        PerformanceAccessContext deptContext = context(List.of(), DataScope.DEPT);
        PerformanceAccessContext personalContext = context(List.of(), DataScope.PERSONAL);

        assertThat(appendWithResolver(deptContext, "pr"))
                .isEqualTo(append(deptContext, "pr"));
        assertThat(appendWithResolver(personalContext, ""))
                .isEqualTo(append(personalContext, ""));
    }

    @Test
    void appendScopeCondition_shouldFailClosedWhenUserOrDeptMissing() {
        PerformanceAccessContext noUser = PerformanceAccessContext.of(null, DEPT, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF));
        PerformanceAccessContext noDept = PerformanceAccessContext.of(USER, null, DataScope.DEPT, List.of(RoleCodes.CHANNEL_LEADER));

        assertThatThrownBy(() -> append(noUser, "pr"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少用户上下文");
        assertThatThrownBy(() -> append(noDept, "pr"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少部门上下文");
    }

    private PerformanceAccessContext context(List<String> roles, DataScope scope) {
        return PerformanceAccessContext.of(USER, DEPT, scope, roles);
    }

    private PerformanceRecord record(UUID channelId, UUID recruiterId) {
        PerformanceRecord record = new PerformanceRecord();
        record.setFinalChannelUserId(channelId);
        record.setFinalRecruiterUserId(recruiterId);
        return record;
    }

    private ScopeResult append(PerformanceAccessContext context, String alias) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        ArrayList<Object> args = new ArrayList<>();
        PerformanceAccessScope.appendScopeCondition(where, args, context, alias, currentUserPermissionChecker);
        return new ScopeResult(where.toString(), args);
    }

    private ScopeResult appendWithResolver(PerformanceAccessContext context, String alias) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        ArrayList<Object> args = new ArrayList<>();
        PerformanceAccessScope.appendScopeConditionWithResolver(
                where,
                args,
                context,
                alias,
                dataScopeResolver,
                currentUserPermissionChecker);
        return new ScopeResult(where.toString(), args);
    }

    private record ScopeResult(String where, List<Object> args) {
    }
}
