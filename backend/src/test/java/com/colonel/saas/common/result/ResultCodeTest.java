package com.colonel.saas.common.result;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultCodeTest {

    @Test
    void success_hasCorrectCodeAndMessage() {
        assertThat(ResultCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ResultCode.SUCCESS.getMsg()).isEqualTo("操作成功");
    }

    @Test
    void paramError_hasCorrectCodeAndMessage() {
        assertThat(ResultCode.PARAM_ERROR.getCode()).isEqualTo(400);
        assertThat(ResultCode.PARAM_ERROR.getMsg()).isEqualTo("参数错误");
    }

    @Test
    void unauthorized_hasCorrectCodeAndMessage() {
        assertThat(ResultCode.UNAUTHORIZED.getCode()).isEqualTo(401);
        assertThat(ResultCode.UNAUTHORIZED.getMsg()).isEqualTo("未授权");
    }

    @Test
    void forbidden_hasCorrectCodeAndMessage() {
        assertThat(ResultCode.FORBIDDEN.getCode()).isEqualTo(403);
        assertThat(ResultCode.FORBIDDEN.getMsg()).isEqualTo("无权限");
    }

    @Test
    void notFound_hasCorrectCodeAndMessage() {
        assertThat(ResultCode.NOT_FOUND.getCode()).isEqualTo(404);
        assertThat(ResultCode.NOT_FOUND.getMsg()).isEqualTo("资源不存在");
    }

    @Test
    void businessError_hasCorrectCodeAndMessage() {
        assertThat(ResultCode.BUSINESS_ERROR.getCode()).isEqualTo(460);
        assertThat(ResultCode.BUSINESS_ERROR.getMsg()).isEqualTo("业务异常");
    }

    @Test
    void serverError_hasCorrectCodeAndMessage() {
        assertThat(ResultCode.SERVER_ERROR.getCode()).isEqualTo(500);
        assertThat(ResultCode.SERVER_ERROR.getMsg()).isEqualTo("服务器异常");
    }
}