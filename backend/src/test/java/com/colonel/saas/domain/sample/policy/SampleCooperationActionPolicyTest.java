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
    void allVisibleActions_shouldBeClickableForEveryRoleAndStatus() {
        UUID ownerUserId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        List<String> visibleActions = List.of(
                SampleCooperationActionPolicy.APPROVE,
                SampleCooperationActionPolicy.REJECT,
                SampleCooperationActionPolicy.EDIT,
                SampleCooperationActionPolicy.PROGRESS,
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
                for (String action : visibleActions) {
                    assertThat(actions.get(action).enabled())
                            .as("action=%s status=%s role=%s", action, status, role)
                            .isTrue();
                }
            }
        }
    }

    private Map<String, SampleActionAvailabilityVO> availability(SampleStatus status) {
        UUID currentUserId = UUID.randomUUID();
        return policy.availability(status, currentUserId, currentUserId, List.of("channel_staff"));
    }
}
