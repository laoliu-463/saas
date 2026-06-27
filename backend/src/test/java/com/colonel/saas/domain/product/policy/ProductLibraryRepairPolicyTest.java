package com.colonel.saas.domain.product.policy;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProductLibraryRepairPolicyTest {

    private final ProductLibraryRepairPolicy policy = new ProductLibraryRepairPolicy();

    @Test
    void decide_shouldSelectPromotingPendingAuditProductWithoutForcingDisplaying() {
        ProductOperationState state = pendingState();
        ProductSnapshot snapshot = snapshot(1, null);
        LocalDateTime now = LocalDateTime.of(2026, 6, 27, 15, 5);

        ProductLibraryRepairPolicy.Decision decision = policy.decide(snapshot, state, now);

        assertThat(decision.changed()).isTrue();
        assertThat(decision.newSelectedToLibrary()).isTrue();
        assertThat(decision.newDisplayStatus()).isEqualTo(ProductDisplayStatus.PENDING.name());
        assertThat(decision.newHiddenReason()).isNull();
        assertThat(decision.newAuditStatus()).isEqualTo(2);
        assertThat(decision.newBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(decision.reason()).isEqualTo(ProductLibraryRepairPolicy.REPAIR_REASON_UPSTREAM_PROMOTING_AUTO_LIBRARY);
        assertThat(decision.willDisplay()).isTrue();
    }

    @Test
    void decide_shouldKeepManualPausedPromotingProductSelectedButHidden() {
        ProductOperationState state = pendingState();
        state.setManualDisabled(true);
        ProductSnapshot snapshot = snapshot(1, null);

        ProductLibraryRepairPolicy.Decision decision = policy.decide(
                snapshot,
                state,
                LocalDateTime.of(2026, 6, 27, 15, 10));

        assertThat(decision.newSelectedToLibrary()).isTrue();
        assertThat(decision.newDisplayStatus()).isEqualTo(ProductDisplayStatus.HIDDEN.name());
        assertThat(decision.newHiddenReason()).isEqualTo(ProductLibraryRepairPolicy.HIDDEN_REASON_LOCAL_PAUSED);
        assertThat(decision.newAuditStatus()).isEqualTo(2);
        assertThat(decision.newBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(decision.reason()).isEqualTo(ProductLibraryRepairPolicy.REPAIR_REASON_LOCAL_PAUSED);
    }

    @Test
    void apply_shouldMutateStateWithDecisionAndRepairMetadata() {
        ProductOperationState state = pendingState();
        ProductLibraryRepairPolicy.Decision decision = policy.decide(
                snapshot(1, null),
                state,
                LocalDateTime.of(2026, 6, 27, 15, 15));
        LocalDateTime appliedAt = LocalDateTime.of(2026, 6, 27, 15, 16);

        policy.apply(state, decision, appliedAt, 3, "上游状态为推广中，系统自动入库展示");

        assertThat(state.getSelectedToLibrary()).isTrue();
        assertThat(state.getSelectedAt()).isEqualTo(appliedAt);
        assertThat(state.getDisplayStatus()).isEqualTo(ProductDisplayStatus.PENDING.name());
        assertThat(state.getHiddenReason()).isNull();
        assertThat(state.getAuditStatus()).isEqualTo(2);
        assertThat(state.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(state.getDisplayRuleVersion()).isEqualTo(3);
        assertThat(state.getAuditRemark()).isEqualTo("上游状态为推广中，系统自动入库展示");
        assertThat(state.getLastOperationAt()).isEqualTo(appliedAt);
    }

    private ProductOperationState pendingState() {
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("ACT-1");
        state.setProductId("P-1");
        state.setSelectedToLibrary(false);
        state.setAuditStatus(1);
        state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
        state.setDisplayStatus(ProductDisplayStatus.PENDING.name());
        return state;
    }

    private ProductSnapshot snapshot(Integer status, String promotionEndTime) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("ACT-1");
        snapshot.setProductId("P-1");
        snapshot.setStatus(status);
        snapshot.setPromotionEndTime(promotionEndTime);
        return snapshot;
    }
}
