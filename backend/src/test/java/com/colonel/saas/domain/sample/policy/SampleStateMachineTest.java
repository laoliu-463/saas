package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SampleStateMachineTest {

    @Test
    void normalizeAction_shouldMapLegacyAliases() {
        assertThat(SampleStateMachine.normalizeAction("approved")).isEqualTo("PENDING_SHIP");
        assertThat(SampleStateMachine.normalizeAction("SHIPPED")).isEqualTo("SHIPPING");
        assertThat(SampleStateMachine.normalizeAction("FINISHED")).isEqualTo("COMPLETED");
    }

    @Test
    void ensureTransition_shouldRejectInvalidFromStatus() {
        assertThatThrownBy(() -> SampleStateMachine.ensureTransition(
                SampleStatus.SHIPPING, SampleStatus.PENDING_AUDIT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Current status does not allow this action: expected")
                .hasMessageContaining("PENDING_AUDIT")
                .hasMessageContaining("合作单当前状态为【SHIPPING】（发货中）")
                .hasMessageContaining("该操作仅在【PENDING_AUDIT】（待审核）状态可用");
    }

    @Test
    void ensurePendingHomeworkTransition_shouldAllowShippingOrDelivered() {
        assertThatCode(() -> SampleStateMachine.ensurePendingHomeworkTransition(SampleStatus.SHIPPING))
                .doesNotThrowAnyException();
        assertThatCode(() -> SampleStateMachine.ensurePendingHomeworkTransition(SampleStatus.DELIVERED))
                .doesNotThrowAnyException();
    }

    @Test
    void isDeletable_shouldOnlyAllowPendingAuditOrRejected() {
        assertThat(SampleStateMachine.isDeletable(SampleStatus.PENDING_AUDIT)).isTrue();
        assertThat(SampleStateMachine.isDeletable(SampleStatus.REJECTED)).isTrue();
        assertThat(SampleStateMachine.isDeletable(SampleStatus.SHIPPING)).isFalse();
    }
}
