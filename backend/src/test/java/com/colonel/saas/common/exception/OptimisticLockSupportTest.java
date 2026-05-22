package com.colonel.saas.common.exception;

import com.colonel.saas.common.result.ResultCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimisticLockSupportTest {

    @Test
    void requireUpdated_zeroRows_throwsConflict() {
        assertThatThrownBy(() -> OptimisticLockSupport.requireUpdated(0))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.CONFLICT.getCode()));
    }

    @Test
    void requireUpdated_positiveRows_doesNotThrow() {
        OptimisticLockSupport.requireUpdated(1);
    }
}
