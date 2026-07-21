package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.vo.sample.SampleActionAvailabilityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SampleCooperationActionPolicyTest {

    private SampleCooperationActionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new SampleCooperationActionPolicy();
    }

    @Test
    void copyActions_shouldBeAvailableBeforeHomeworkCompletionOrClosure() {
        for (SampleStatus status : List.of(
                SampleStatus.PENDING_AUDIT,
                SampleStatus.PENDING_SHIP,
                SampleStatus.SHIPPING,
                SampleStatus.DELIVERED,
                SampleStatus.REJECTED)) {
            Map<String, SampleActionAvailabilityVO> actions = availability(status);

            assertThat(actions.get(SampleCooperationActionPolicy.COPY_LINK).enabled()).isTrue();
            assertThat(actions.get(SampleCooperationActionPolicy.COPY_ORDER).enabled()).isTrue();
        }
    }

    @Test
    void copyActions_shouldRemainAvailableAfterHomeworkCompletionOrClosure() {
        for (SampleStatus status : List.of(
                SampleStatus.PENDING_HOMEWORK,
                SampleStatus.COMPLETED,
                SampleStatus.CLOSED)) {
            Map<String, SampleActionAvailabilityVO> actions = availability(status);

            assertThat(actions.get(SampleCooperationActionPolicy.COPY_LINK).enabled()).isTrue();
            assertThat(actions.get(SampleCooperationActionPolicy.COPY_ORDER).enabled()).isTrue();
        }
    }

    @Test
    void copyAndNoteActions_shouldBeAvailableForEveryRoleAndStatus() {
        // PR #fix-cooperation-action-availability: 拆分原 allVisibleActions_shouldBeClickableForEveryRoleAndStatus
        // 原测试对所有 action 期望 enabled=true，但状态机操作（APPROVE/REJECT/PROGRESS）
        // 必须按当前 status 严格限制。COPY_LINK / COPY_ORDER / NOTE 不是状态机操作，
        // 任何 status 都可用。
        UUID ownerUserId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        List<String> statelessActions = List.of(
                SampleCooperationActionPolicy.COPY_LINK,
                SampleCooperationActionPolicy.COPY_ORDER,
                SampleCooperationActionPolicy.NOTE);
        List<String> roles = List.of(
                RoleCodes.ADMIN,
                RoleCodes.BIZ_LEADER,
                RoleCodes.BIZ_STAFF,
                RoleCodes.CHANNEL_LEADER,
                RoleCodes.CHANNEL_STAFF,
                RoleCodes.OPS_STAFF);

        for (SampleStatus status : SampleStatus.values()) {
            for (String role : roles) {
                Map<String, SampleActionAvailabilityVO> actions = policy.availability(
                        status,
                        ownerUserId,
                        currentUserId,
                        List.of(role));
                for (String action : statelessActions) {
                    assertThat(actions.get(action).enabled())
                            .as("action=%s status=%s role=%s", action, status, role)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void approveAndReject_shouldBeEnabledOnlyWhenPendingAudit() {
        // PR #fix-cooperation-action-availability: APPROVE/REJECT 仅 PENDING_AUDIT 可用
        // 防止 SHIPPING 状态误点确认通过（生产 bug 报告：3 个 traceId 都是这个原因）
        for (SampleStatus status : SampleStatus.values()) {
            Map<String, SampleActionAvailabilityVO> actions = availability(status);
            boolean shouldBeEnabled = (status == SampleStatus.PENDING_AUDIT);
            assertThat(actions.get(SampleCooperationActionPolicy.APPROVE).enabled())
                    .as("APPROVE at %s", status).isEqualTo(shouldBeEnabled);
            assertThat(actions.get(SampleCooperationActionPolicy.REJECT).enabled())
                    .as("REJECT at %s", status).isEqualTo(shouldBeEnabled);
        }
    }

    @Test
    void progress_shouldBeEnabledOnlyWhenPendingShip() {
        // PR #fix-cooperation-action-availability: PROGRESS 仅 PENDING_SHIP 可用
        for (SampleStatus status : SampleStatus.values()) {
            Map<String, SampleActionAvailabilityVO> actions = availability(status);
            boolean shouldBeEnabled = (status == SampleStatus.PENDING_SHIP);
            assertThat(actions.get(SampleCooperationActionPolicy.PROGRESS).enabled())
                    .as("PROGRESS at %s", status).isEqualTo(shouldBeEnabled);
        }
    }

    @Test
    void edit_shouldBeEnabledOnlyForEditableStatuses() {
        // PR #fix-cooperation-action-availability: EDIT 按 EDITABLE_STATUSES 限制
        // (与 ensureCanEdit 一致：PENDING_AUDIT / PENDING_SHIP / SHIPPING / REJECTED)
        for (SampleStatus status : SampleStatus.values()) {
            Map<String, SampleActionAvailabilityVO> actions = availability(status);
            boolean shouldBeEnabled = status == SampleStatus.PENDING_AUDIT
                    || status == SampleStatus.PENDING_SHIP
                    || status == SampleStatus.SHIPPING
                    || status == SampleStatus.REJECTED;
            assertThat(actions.get(SampleCooperationActionPolicy.EDIT).enabled())
                    .as("EDIT at %s", status).isEqualTo(shouldBeEnabled);
        }
    }

    @Test
    void disabledReason_shouldBePresentWhenActionIsUnavailable() {
        // PR #fix-cooperation-action-availability: 不可用 action 必须带原因
        Map<String, SampleActionAvailabilityVO> actions = availability(SampleStatus.SHIPPING);
        assertThat(actions.get(SampleCooperationActionPolicy.APPROVE).enabled()).isFalse();
        assertThat(actions.get(SampleCooperationActionPolicy.APPROVE).disabledReason())
                .isNotBlank();
        assertThat(actions.get(SampleCooperationActionPolicy.PROGRESS).enabled()).isFalse();
        assertThat(actions.get(SampleCooperationActionPolicy.PROGRESS).disabledReason())
                .isNotBlank();
    }

    private Map<String, SampleActionAvailabilityVO> availability(SampleStatus status) {
        UUID currentUserId = UUID.randomUUID();
        return policy.availability(status, currentUserId, currentUserId, List.of("channel_staff"));
    }
}
