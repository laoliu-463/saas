package com.colonel.saas.service.performance;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.PerformanceRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceAccessScopeTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void canExport_shouldAllowLeadersAndAdmin() {
        assertThat(PerformanceAccessScope.canExport(context(List.of(RoleCodes.ADMIN), DataScope.ALL))).isTrue();
        assertThat(PerformanceAccessScope.canExport(context(List.of(RoleCodes.BIZ_LEADER), DataScope.DEPT))).isTrue();
        assertThat(PerformanceAccessScope.canExport(context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL))).isFalse();
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
  void appendScopeCondition_shouldAddChannelStaffFilter() {
    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    java.util.ArrayList<Object> args = new java.util.ArrayList<>();
    PerformanceAccessScope.appendScopeCondition(
        where,
        args,
        context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL),
        "pr");
    assertThat(where.toString()).contains("final_channel_user_id = ?");
    assertThat(args).containsExactly(USER);
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
}
