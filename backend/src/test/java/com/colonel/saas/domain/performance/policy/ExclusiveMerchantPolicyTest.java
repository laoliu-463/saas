package com.colonel.saas.domain.performance.policy;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class ExclusiveMerchantPolicyTest {

    @Test
    void meets_aboveThreshold_returnsTrue() {
        boolean ok = ExclusiveMerchantPolicy.meets(new BigDecimal("75.00"), new BigDecimal("70.00"));
        assertThat(ok).isTrue();
    }

    @Test
    void meets_belowThreshold_returnsFalse() {
        boolean ok = ExclusiveMerchantPolicy.meets(new BigDecimal("65.00"), new BigDecimal("70.00"));
        assertThat(ok).isFalse();
    }

    @Test
    void meets_nullCases_returnsFalse() {
        assertThat(ExclusiveMerchantPolicy.meets(null, new BigDecimal("70"))).isFalse();
        assertThat(ExclusiveMerchantPolicy.meets(new BigDecimal("70"), null)).isFalse();
    }
}
