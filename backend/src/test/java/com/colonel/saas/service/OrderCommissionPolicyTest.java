package com.colonel.saas.service;

import com.colonel.saas.domain.order.policy.OrderCommissionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCommissionPolicyTest {

    @Test
    void countsTowardCommission_shouldExcludeCancelledAndRefunded() {
        assertThat(OrderCommissionPolicy.countsTowardCommission(null)).isTrue();
        assertThat(OrderCommissionPolicy.countsTowardCommission(1)).isTrue();
        assertThat(OrderCommissionPolicy.countsTowardCommission(3)).isTrue();
        assertThat(OrderCommissionPolicy.countsTowardCommission(OrderCommissionPolicy.STATUS_CANCELLED)).isFalse();
        assertThat(OrderCommissionPolicy.countsTowardCommission(OrderCommissionPolicy.STATUS_REFUNDED)).isFalse();
    }

    @Test
    void countsTowardPerformance_shouldMatchCommissionPolicy() {
        assertThat(OrderCommissionPolicy.countsTowardPerformance(1))
                .isEqualTo(OrderCommissionPolicy.countsTowardCommission(1));
        assertThat(OrderCommissionPolicy.countsTowardPerformance(OrderCommissionPolicy.STATUS_REFUNDED)).isFalse();
    }
}
