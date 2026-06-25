package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SampleActionPermissionPolicyTest {

    private SampleActionPermissionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new SampleActionPermissionPolicy(new CurrentUserPermissionPolicy());
    }

    @Test
    void applyAndDelete_shouldAllowOnlyChannelRolesOrAdmin() {
        assertThatCode(() -> policy.ensureCanApply(List.of(RoleCodes.ADMIN))).doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanApply(List.of(RoleCodes.CHANNEL_LEADER))).doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanApply(List.of(RoleCodes.CHANNEL_STAFF))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.ensureCanApply(List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅渠道角色可以发起寄样申请");

        assertThatCode(() -> policy.ensureCanDelete(List.of(RoleCodes.CHANNEL_STAFF))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.ensureCanDelete(List.of(RoleCodes.OPS_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅渠道角色可以删除寄样申请");
    }

    @Test
    void actionTransition_shouldKeepReviewAndLogisticsRoleGroups() {
        assertThatCode(() -> policy.ensureCanPerformAction("PENDING_SHIP", List.of(RoleCodes.BIZ_STAFF)))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanPerformAction("REJECTED", List.of(RoleCodes.ADMIN)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.ensureCanPerformAction("REJECTED", List.of(RoleCodes.OPS_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅招商角色可以审核寄样");

        assertThatCode(() -> policy.ensureCanPerformAction("SHIPPING", List.of(RoleCodes.OPS_STAFF)))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanPerformAction("DELIVERED", List.of(RoleCodes.ADMIN)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.ensureCanPerformAction("PENDING_HOMEWORK", List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅运营角色可以推进物流状态");

        assertThatThrownBy(() -> policy.ensureCanPerformAction("COMPLETED", List.of(RoleCodes.ADMIN)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("完成与关闭状态仅允许系统自动推进");
        assertThatCode(() -> policy.ensureCanPerformAction("UNKNOWN", List.of(RoleCodes.CHANNEL_STAFF)))
                .doesNotThrowAnyException();
    }

    @Test
    void exportAndLogisticsSync_shouldKeepCurrentRoleGroups() {
        assertThatCode(() -> policy.ensureCanSyncLogistics(List.of(RoleCodes.OPS_STAFF)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.ensureCanSyncLogistics(List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅运营或管理员可触发物流同步");

        assertThatCode(() -> policy.ensureCanExport(List.of(RoleCodes.BIZ_LEADER))).doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanExport(List.of(RoleCodes.OPS_STAFF))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.ensureCanExport(List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅管理员、招商或运营可导出寄样数据");
    }

    @Test
    void logisticsImport_shouldKeepImportAndOverwriteRoleGroups() {
        assertThatCode(() -> policy.ensureCanImportLogistics(List.of(" OPS_STAFF ")))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanImportLogistics(List.of(RoleCodes.ADMIN)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.ensureCanImportLogistics(List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅运营或管理员可导入物流单号");

        assertThat(policy.canImportLogistics("[ OPS_STAFF ]")).isTrue();
        assertThat(policy.canOverwriteLogisticsImport("[ ADMIN ]")).isTrue();
        assertThat(policy.canOverwriteLogisticsImport(List.of(RoleCodes.OPS_STAFF))).isFalse();
        assertThatThrownBy(() -> policy.ensureCanOverwriteLogisticsImport(List.of(RoleCodes.OPS_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅管理员可覆盖已有物流单号");
    }

    @Test
    void predicates_shouldExposeSamplePermissionLanguage() {
        assertThat(policy.isSevenDayLimitExempt(List.of(RoleCodes.ADMIN))).isTrue();
        assertThat(policy.isSevenDayLimitExempt("[" + RoleCodes.CHANNEL_LEADER + "]")).isTrue();
        assertThat(policy.isSevenDayLimitExempt(List.of(RoleCodes.CHANNEL_STAFF))).isFalse();

        assertThat(policy.isOpsStaffOnly(List.of(RoleCodes.OPS_STAFF))).isTrue();
        assertThat(policy.isOpsStaffOnly(List.of(RoleCodes.OPS_STAFF, RoleCodes.ADMIN))).isFalse();

        assertThat(policy.requiresChannelTalentClaim(List.of(RoleCodes.CHANNEL_STAFF))).isTrue();
        assertThat(policy.requiresChannelTalentClaim(List.of(RoleCodes.CHANNEL_LEADER))).isTrue();
        assertThat(policy.requiresChannelTalentClaim(List.of(RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN))).isFalse();
        assertThat(policy.requiresChannelTalentClaim(List.of(RoleCodes.BIZ_STAFF))).isFalse();
    }
}
