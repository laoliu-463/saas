package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.vo.sample.SampleActionAvailabilityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SampleCooperationActionPolicyTest {

    private SampleCooperationActionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new SampleCooperationActionPolicy(
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()));
    }

    @Test
    void availability_shouldAlwaysReturnEightActionsInFixedOrder() {
        UUID ownerId = UUID.randomUUID();

        Map<String, SampleActionAvailabilityVO> actions = policy.availability(
                SampleStatus.PENDING_AUDIT,
                ownerId,
                ownerId,
                List.of(RoleCodes.BIZ_STAFF));

        assertThat(new ArrayList<>(actions.keySet())).containsExactly(
                "APPROVE", "REJECT", "EDIT", "PROGRESS",
                "COPY_LINK", "COPY_ORDER", "COMPLAIN", "NOTE");
        assertThat(actions.values()).allSatisfy(action -> {
            assertThat(action).isNotNull();
            if (action.enabled()) {
                assertThat(action.disabledReason()).isNull();
            } else {
                assertThat(action.disabledReason()).isNotBlank();
            }
        });
    }

    @Test
    void approveAndReject_shouldRequirePendingAuditAndAdminOrBizStaff() {
        UUID ownerId = UUID.randomUUID();

        assertThat(policy.availability(
                SampleStatus.PENDING_AUDIT, ownerId, UUID.randomUUID(), List.of(RoleCodes.BIZ_STAFF))
                .get("APPROVE").enabled()).isTrue();
        assertThat(policy.availability(
                SampleStatus.PENDING_AUDIT, ownerId, UUID.randomUUID(), List.of(RoleCodes.ADMIN))
                .get("REJECT").enabled()).isTrue();
        assertThat(policy.availability(
                SampleStatus.PENDING_AUDIT, ownerId, UUID.randomUUID(), List.of(RoleCodes.BIZ_LEADER))
                .get("APPROVE").enabled()).isFalse();
        assertThat(policy.availability(
                SampleStatus.PENDING_SHIP, ownerId, UUID.randomUUID(), List.of(RoleCodes.ADMIN))
                .get("REJECT").enabled()).isFalse();
    }

    @Test
    void edit_shouldRequireOwnerOrAdminAndAnEditableStatus() {
        UUID ownerId = UUID.randomUUID();

        for (SampleStatus status : List.of(
                SampleStatus.PENDING_AUDIT,
                SampleStatus.PENDING_SHIP,
                SampleStatus.SHIPPING,
                SampleStatus.REJECTED)) {
            assertThat(policy.availability(status, ownerId, ownerId, List.of(RoleCodes.CHANNEL_STAFF))
                    .get("EDIT").enabled()).isTrue();
            assertThat(policy.availability(status, ownerId, UUID.randomUUID(), List.of(RoleCodes.ADMIN))
                    .get("EDIT").enabled()).isTrue();
        }

        assertThat(policy.availability(
                SampleStatus.PENDING_AUDIT, ownerId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF))
                .get("EDIT").enabled()).isFalse();
        assertThat(policy.availability(
                SampleStatus.PENDING_HOMEWORK, ownerId, ownerId, List.of(RoleCodes.CHANNEL_STAFF))
                .get("EDIT").enabled()).isFalse();
        assertThat(policy.availability(
                SampleStatus.COMPLETED, ownerId, UUID.randomUUID(), List.of(RoleCodes.ADMIN))
                .get("EDIT").enabled()).isFalse();
        assertThat(policy.availability(
                SampleStatus.CLOSED, ownerId, UUID.randomUUID(), List.of(RoleCodes.ADMIN))
                .get("EDIT").enabled()).isFalse();
    }

    @Test
    void visibleRowActions_shouldNeverDropKeysAndShouldReflectCurrentBackendCapabilities() {
        Map<String, SampleActionAvailabilityVO> actions = policy.availability(
                SampleStatus.PENDING_HOMEWORK,
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(RoleCodes.OPS_STAFF));

        assertThat(actions.get("PROGRESS").enabled()).isTrue();
        assertThat(actions.get("NOTE").enabled()).isTrue();
        assertThat(actions.get("COPY_LINK").enabled()).isFalse();
        assertThat(actions.get("COPY_ORDER").enabled()).isFalse();
        assertThat(actions.get("COMPLAIN").enabled()).isFalse();
    }

    @Test
    void systemOwnedProgressAndTerminalStatuses_shouldNotExposeManualDecisionOrEditActions() {
        UUID ownerId = UUID.randomUUID();

        for (SampleStatus status : List.of(
                SampleStatus.PENDING_HOMEWORK,
                SampleStatus.COMPLETED,
                SampleStatus.CLOSED)) {
            Map<String, SampleActionAvailabilityVO> actions = policy.availability(
                    status, ownerId, ownerId, List.of(RoleCodes.ADMIN));

            assertThat(actions).hasSize(8);
            assertThat(actions.get("APPROVE").enabled()).isFalse();
            assertThat(actions.get("REJECT").enabled()).isFalse();
            assertThat(actions.get("EDIT").enabled()).isFalse();
            assertThat(actions).doesNotContainKeys("COMPLETE", "CLOSE");
        }

        assertThat(SampleStatus.fromApiStatus("PENDING_TASK")).isEqualTo(SampleStatus.PENDING_HOMEWORK);
        assertThat(SampleStatus.fromApiStatus("FINISHED")).isEqualTo(SampleStatus.COMPLETED);
    }

    @Test
    void remarkPolicy_shouldPreferStructuredReasonStripLegacySpecificationAndPreserveOtherExtraData() {
        SampleRemarkPolicy remarkPolicy = new SampleRemarkPolicy();
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("specification", "红色 / M");
        extra.put("preserved", "yes");

        assertThat(remarkPolicy.resolve(extra, "规格: 红色 / M；  历史原因  "))
                .isEqualTo("历史原因");

        extra.put("applyReason", "  结构化原因  ");
        assertThat(remarkPolicy.resolve(extra, "规格: 红色 / M；历史原因"))
                .isEqualTo("结构化原因");

        SampleRequest sample = new SampleRequest();
        sample.setExtraData(extra);
        remarkPolicy.apply(sample, "  更新原因  ");
        assertThat(sample.getRemark()).isEqualTo("更新原因");
        assertThat(sample.getExtraData())
                .containsEntry("applyReason", "更新原因")
                .containsEntry("preserved", "yes")
                .containsEntry("specification", "红色 / M");

        String historicalReason = "历史".repeat(101);
        assertThat(remarkPolicy.resolve(
                Map.of("applyReason", "  " + historicalReason + "  "),
                null)).isEqualTo(historicalReason);

        SampleRequest tooLong = new SampleRequest();
        assertThatThrownBy(() -> remarkPolicy.apply(tooLong, historicalReason))
                .hasMessageContaining("200");
    }
}
