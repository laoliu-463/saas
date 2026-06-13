package com.colonel.saas.domain.talent.policy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExclusiveTalentPolicyTest {

    @Test
    void meets_bothAbove_returnsTrue() {
        boolean ok = ExclusiveTalentPolicy.meets(
                new BigDecimal("80.00"), new BigDecimal("70.00"),
                15, 10);
        assertThat(ok).isTrue();
    }

    @Test
    void meets_ratioBelowThreshold_returnsFalse() {
        boolean ok = ExclusiveTalentPolicy.meets(
                new BigDecimal("65.00"), new BigDecimal("70.00"),
                15, 10);
        assertThat(ok).isFalse();
    }

    @Test
    void meets_sampleBelowThreshold_returnsFalse() {
        boolean ok = ExclusiveTalentPolicy.meets(
                new BigDecimal("90.00"), new BigDecimal("70.00"),
                5, 10);
        assertThat(ok).isFalse();
    }

    @Test
    void meets_bothBelow_returnsFalse() {
        boolean ok = ExclusiveTalentPolicy.meets(
                new BigDecimal("50.00"), new BigDecimal("70.00"),
                3, 10);
        assertThat(ok).isFalse();
    }

    @Test
    void meets_ratioAtThreshold_returnsTrue() {
        boolean ok = ExclusiveTalentPolicy.meets(
                new BigDecimal("70.00"), new BigDecimal("70.00"),
                10, 10);
        assertThat(ok).isTrue();
    }

    @Test
    void meets_nullRatio_returnsFalse() {
        assertThat(ExclusiveTalentPolicy.meets(null, new BigDecimal("70"), 10, 10)).isFalse();
        assertThat(ExclusiveTalentPolicy.meets(new BigDecimal("70"), null, 10, 10)).isFalse();
    }

    @Test
    void computeRatio_zeroTotal_returnsZero() {
        BigDecimal r = ExclusiveTalentPolicy.computeRatio(100, 0);
        assertThat(r).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeRatio_normalCase() {
        BigDecimal r = ExclusiveTalentPolicy.computeRatio(70, 100);
        assertThat(r).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    void computeRatio_halfUpRounding() {
        BigDecimal r = ExclusiveTalentPolicy.computeRatio(1, 3);
        assertThat(r).isEqualByComparingTo(new BigDecimal("33.33"));
    }
}