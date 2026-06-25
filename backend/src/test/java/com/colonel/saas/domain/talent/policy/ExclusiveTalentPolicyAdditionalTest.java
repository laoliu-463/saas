package com.colonel.saas.domain.talent.policy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExclusiveTalentPolicyAdditionalTest {

    @Test
    void meets_exactThreshold_isTrue() {
        assertThat(ExclusiveTalentPolicy.meets(
                new BigDecimal("70.000"), new BigDecimal("70.00"), 10, 10)).isTrue();
    }

    @Test
    void meets_justBelowThreshold_isFalse() {
        assertThat(ExclusiveTalentPolicy.meets(
                new BigDecimal("69.99"), new BigDecimal("70.00"), 10, 10)).isFalse();
    }

    @Test
    void meets_sampleAtThreshold_isTrue() {
        assertThat(ExclusiveTalentPolicy.meets(
                new BigDecimal("80.00"), new BigDecimal("70.00"), 10, 10)).isTrue();
    }

    @Test
    void meets_sampleJustBelowThreshold_isFalse() {
        assertThat(ExclusiveTalentPolicy.meets(
                new BigDecimal("80.00"), new BigDecimal("70.00"), 9, 10)).isFalse();
    }

    @Test
    void computeRatio_handlesNegativeChannelFee() {
        // negative channel fee would be a data anomaly; should still return some ratio
        BigDecimal r = ExclusiveTalentPolicy.computeRatio(-50, 100);
        assertThat(r).isLessThan(new BigDecimal("0"));
    }

    @Test
    void computeRatio_largeValues() {
        // 999999999 / 1000000000 * 100 = 99.9999999 -> HALF_UP rounds to 100.00
        BigDecimal r = ExclusiveTalentPolicy.computeRatio(999_999_999L, 1_000_000_000L);
        assertThat(r).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
