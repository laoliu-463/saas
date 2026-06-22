package com.colonel.saas.domain.performance.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
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

    @Test
    void canExport_shouldAllowLeadersAndAdmin() {
        assertThat(PerformanceAccessScope.canExport(null)).isFalse();
        assertThat(PerformanceAccessScope.canExport(context(List.of(" ADMIN "), DataScope.PERSONAL))).isTrue();
        assertThat(PerformanceAccessScope.canExport(context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT))).isTrue();
        assertThat(PerformanceAccessScope.canExport(context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT))).isTrue();
        assertThat(PerformanceAccessScope.canExport(context(List.of(RoleCodes.CHANNEL_LEADER), DataScope.DEPT))).isTrue();
        assertThat(PerformanceAccessScope.canExport(context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL))).isFalse();
    }

    @Test
    void canRecalculateMonth_shouldAllowOnlyAdmin() {
        assertThat(PerformanceAccessScope.canRecalculateMonth(null)).isFalse();
        assertThat(PerformanceAccessScope.canRecalculateMonth(context(List.of(RoleCodes.ADMIN), DataScope.PERSONAL))).isTrue();
        assertThat(PerformanceAccessScope.canRecalculateMonth(context(List.of(RoleCodes.OPS_STAFF), DataScope.ALL))).isFalse();
    }

    @Test
    void assertFilterAllowed_shouldRejectMissingContextAndCrossStaffFilters() {
        assertThatThrownBy(() -> PerformanceAccessScope.assertFilterAllowed(USER, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问业绩数据");
        assertThatThrownBy(() -> PerformanceAccessScope.assertFilterAllowed(OTHER, null,
                context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权按该渠道筛选业绩");
        assertThatThrownBy(() -> PerformanceAccessScope.assertFilterAllowed(null, OTHER,
                context(List.of(RoleCodes.BIZ_STAFF), DataScope.PERSONAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权按该招商筛选业绩");
    }

    @Test
    void assertFilterAllowed_shouldAllowAdminLikeAndOwnStaffFilters() {
        PerformanceAccessScope.assertFilterAllowed(OTHER, OTHER, context(List.of(RoleCodes.OPS_STAFF), DataScope.PERSONAL));
        PerformanceAccessScope.assertFilterAllowed(OTHER, OTHER, context(List.of(), DataScope.ALL));
        PerformanceAccessScope.assertFilterAllowed(USER, null, context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL));
        PerformanceAccessScope.assertFilterAllowed(null, USER, context(List.of(RoleCodes.BIZ_STAFF), DataScope.PERSONAL));
        PerformanceAccessScope.assertFilterAllowed(OTHER, OTHER, context(List.of(RoleCodes.CHANNEL_LEADER), DataScope.DEPT));
        PerformanceAccessScope.assertFilterAllowed(OTHER, OTHER, context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT));
    }

    @Test
    void canAccessRecord_shouldRejectMissingRecordContextAndUser() {
        assertThat(PerformanceAccessScope.canAccessRecord(null, context(List.of(RoleCodes.ADMIN), DataScope.ALL))).isFalse();
        assertThat(PerformanceAccessScope.canAccessRecord(record(USER, USER), null)).isFalse();
        PerformanceAccessContext noUser = PerformanceAccessContext.of(null, DEPT, DataScope.PERSONAL, List.of());
        assertThat(PerformanceAccessScope.canAccessRecord(record(USER, USER), noUser)).isFalse();
    }

    @Test
    void canAccessRecord_shouldAllowAdminLike() {
        assertThat(PerformanceAccessScope.canAccessRecord(record(OTHER, OTHER), context(List.of(RoleCodes.ADMIN), DataScope.PERSONAL)))
                .isTrue();
        assertThat(PerformanceAccessScope.canAccessRecord(record(OTHER, OTHER), context(List.of(), DataScope.ALL)))
                .isTrue();
    }

    @Test
    void canAccessRecord_shouldRestrictChannelStaff() {
        PerformanceRecord record = record(OTHER, USER);
        assertThat(PerformanceAccessScope.canAccessRecord(record, context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL)))
                .isFalse();
        record.setFinalChannelUserId(USER);
        assertThat(PerformanceAccessScope.canAccessRecord(record, context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL)))
                .isTrue();
    }

    @Test
    void canAccessRecord_shouldRestrictRecruiterStaff() {
        PerformanceRecord record = record(OTHER, USER);
        assertThat(PerformanceAccessScope.canAccessRecord(record, context(List.of(RoleCodes.BIZ_STAFF), DataScope.PERSONAL)))
                .isTrue();
        record.setFinalRecruiterUserId(OTHER);
        assertThat(PerformanceAccessScope.canAccessRecord(record, context(List.of(RoleCodes.BIZ_STAFF), DataScope.PERSONAL)))
                .isFalse();
    }

    @Test
    void canAccessRecord_shouldRestrictLeadersToMatchingDeptMemberPlaceholder() {
        assertThat(PerformanceAccessScope.canAccessRecord(record(USER, OTHER),
                context(List.of(RoleCodes.CHANNEL_LEADER), DataScope.DEPT))).isTrue();
        assertThat(PerformanceAccessScope.canAccessRecord(record(OTHER, USER),
                context(List.of(RoleCodes.CHANNEL_LEADER), DataScope.DEPT))).isFalse();
        assertThat(PerformanceAccessScope.canAccessRecord(record(OTHER, USER),
                context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT))).isTrue();
        assertThat(PerformanceAccessScope.canAccessRecord(record(USER, OTHER),
                context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT))).isFalse();
    }

    @Test
    void canAccessRecord_shouldApplyPersonalFallbackAndAllowDeptFallback() {
        assertThat(PerformanceAccessScope.canAccessRecord(record(OTHER, USER), context(List.of(), DataScope.PERSONAL)))
                .isTrue();
        assertThat(PerformanceAccessScope.canAccessRecord(record(OTHER, OTHER), context(List.of(), DataScope.PERSONAL)))
                .isFalse();
        assertThat(PerformanceAccessScope.canAccessRecord(record(OTHER, OTHER), context(List.of(), DataScope.DEPT)))
                .isTrue();
    }

    @Test
    void appendScopeCondition_shouldIgnoreMissingWhereOrContextAndAdminLike() {
        ArrayList<Object> args = new ArrayList<>();
        PerformanceAccessScope.appendScopeCondition(null, args, context(List.of(), DataScope.PERSONAL), "pr");
        PerformanceAccessScope.appendScopeCondition(new StringBuilder("WHERE 1=1"), args, null, "pr");
        StringBuilder where = new StringBuilder("WHERE 1=1");
        PerformanceAccessScope.appendScopeCondition(where, args, context(List.of(RoleCodes.ADMIN), DataScope.PERSONAL), "pr");

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

        assertThat(channel.where()).contains("pr.final_channel_user_id IN (SELECT id FROM sys_user WHERE dept_id = ? AND deleted = 0)");
        assertThat(channel.args()).containsExactly(DEPT);
        assertThat(recruiter.where()).contains("pr.final_recruiter_user_id IN (SELECT id FROM sys_user WHERE dept_id = ? AND deleted = 0)");
        assertThat(recruiter.args()).containsExactly(DEPT);
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
    void appendScopeConditionWithPolicy_shouldPreserveDeptAndPersonalFallbackSql() {
        DataScopePolicy dataScopePolicy = new DataScopePolicy();
        PerformanceAccessContext deptContext = context(List.of(), DataScope.DEPT);
        PerformanceAccessContext personalContext = context(List.of(), DataScope.PERSONAL);

        assertThat(appendWithPolicy(deptContext, "pr", dataScopePolicy))
                .isEqualTo(append(deptContext, "pr"));
        assertThat(appendWithPolicy(personalContext, "", dataScopePolicy))
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
        PerformanceAccessScope.appendScopeCondition(where, args, context, alias);
        return new ScopeResult(where.toString(), args);
    }

    private ScopeResult appendWithPolicy(PerformanceAccessContext context, String alias, DataScopePolicy dataScopePolicy) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        ArrayList<Object> args = new ArrayList<>();
        PerformanceAccessScope.appendScopeConditionWithPolicy(where, args, context, alias, dataScopePolicy);
        return new ScopeResult(where.toString(), args);
    }

    private record ScopeResult(String where, List<Object> args) {
    }
}
