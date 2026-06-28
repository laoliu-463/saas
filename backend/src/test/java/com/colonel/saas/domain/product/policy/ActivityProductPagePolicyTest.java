package com.colonel.saas.domain.product.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityProductPagePolicyTest {

    @Test
    void normalizePageSize_shouldClampToActivityProductApiLimit() {
        assertThat(ActivityProductPagePolicy.normalizePageSize(null)).isEqualTo(20);
        assertThat(ActivityProductPagePolicy.normalizePageSize(0)).isEqualTo(1);
        assertThat(ActivityProductPagePolicy.normalizePageSize(100)).isEqualTo(20);
        assertThat(ActivityProductPagePolicy.normalizePageSize(12)).isEqualTo(12);
    }

    @Test
    void parseCursor_shouldUseNonNegativeOffsetFallback() {
        assertThat(ActivityProductPagePolicy.parseCursor(null)).isZero();
        assertThat(ActivityProductPagePolicy.parseCursor("")).isZero();
        assertThat(ActivityProductPagePolicy.parseCursor("-10")).isZero();
        assertThat(ActivityProductPagePolicy.parseCursor("bad")).isZero();
        assertThat(ActivityProductPagePolicy.parseCursor(" 40 ")).isEqualTo(40);
    }

    @Test
    void hasMoreAndNextCursor_shouldUseFilteredBackendTotal() {
        assertThat(ActivityProductPagePolicy.hasMore(40L, 20)).isTrue();
        assertThat(ActivityProductPagePolicy.nextCursor(40L, 20)).isEqualTo("20");

        assertThat(ActivityProductPagePolicy.hasMore(40L, 40)).isFalse();
        assertThat(ActivityProductPagePolicy.nextCursor(40L, 40)).isEmpty();

        assertThat(ActivityProductPagePolicy.hasMore(35L, 40)).isFalse();
        assertThat(ActivityProductPagePolicy.nextCursor(35L, 40)).isEmpty();
    }

    @Test
    void responseTotal_shouldPreserveFilteredTotalWhenAvailable() {
        assertThat(ActivityProductPagePolicy.responseTotal(35L, 20)).isEqualTo(35);
        assertThat(ActivityProductPagePolicy.responseTotal(null, 8)).isEqualTo(8);
    }
}
