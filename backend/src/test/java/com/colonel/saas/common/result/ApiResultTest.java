package com.colonel.saas.common.result;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResultTest {

    @Test
    void ok_nullData_returnsSuccessWithNullData() {
        ApiResult<Void> result = ApiResult.ok();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMsg()).isEqualTo("操作成功");
        assertThat(result.getData()).isNull();
        assertThat(result.getTimestamp()).isGreaterThan(0);
    }

    @Test
    void ok_withData_returnsSuccessWithData() {
        ApiResult<String> result = ApiResult.ok("hello");
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMsg()).isEqualTo("操作成功");
        assertThat(result.getData()).isEqualTo("hello");
    }

    @Test
    void fail_returnsBusinessErrorWithMessage() {
        ApiResult<Void> result = ApiResult.fail("用户名已存在");
        assertThat(result.getCode()).isEqualTo(460);
        assertThat(result.getMsg()).isEqualTo("用户名已存在");
        assertThat(result.getData()).isNull();
    }

    @Test
    void of_withResultCodeAndData_returnsCorrectResult() {
        ApiResult<String> result = ApiResult.of(ResultCode.SUCCESS, "data");
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMsg()).isEqualTo("操作成功");
        assertThat(result.getData()).isEqualTo("data");
    }

    @Test
    void of_withCodeMsgData_returnsCorrectResult() {
        ApiResult<String> result = ApiResult.of(400, "参数错误", null);
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("参数错误");
        assertThat(result.getData()).isNull();
    }

    @Test
    void timestamp_isSetOnEveryFactoryCall() {
        long before = System.currentTimeMillis();
        ApiResult<Void> result = ApiResult.ok();
        long after = System.currentTimeMillis();
        assertThat(result.getTimestamp()).isBetween(before, after);
    }
}