package com.colonel.saas.common.exception;

import com.colonel.saas.common.result.ResultCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void factories_useSemanticResultCodes() {
        assertThat(BusinessException.notFound("x").getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(BusinessException.conflict("x").getCode()).isEqualTo(ResultCode.CONFLICT.getCode());
        assertThat(BusinessException.stateInvalid("x").getCode()).isEqualTo(ResultCode.STATE_INVALID.getCode());
        assertThat(BusinessException.duplicate("x").getCode()).isEqualTo(ResultCode.DUPLICATE.getCode());
        assertThat(BusinessException.idempotencyInProgress("x").getCode())
                .isEqualTo(ResultCode.IDEMPOTENCY_IN_PROGRESS.getCode());
        assertThat(BusinessException.param("x").getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(BusinessException.forbidden("x").getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
        assertThat(BusinessException.external("x").getCode()).isEqualTo(ResultCode.EXTERNAL_SERVICE.getCode());
    }

    @Test
    void deprecatedStringConstructor_usesParamErrorCode() {
        assertThat(new BusinessException("generic").getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    void businessFactory_usesBusinessErrorCode() {
        assertThat(BusinessException.business("generic").getCode()).isEqualTo(ResultCode.BUSINESS_ERROR.getCode());
    }
}
