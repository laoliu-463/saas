package com.colonel.saas.common.result;

import lombok.Data;

import java.io.Serializable;

@Data
public class ApiResult<T> implements Serializable {
    private int code;
    private String msg;
    private T data;
    private long timestamp;

    public static <T> ApiResult<T> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> ok(T data) {
        return of(ResultCode.SUCCESS, data);
    }

    public static <T> ApiResult<T> fail(String msg) {
        return of(ResultCode.BUSINESS_ERROR.getCode(), msg, null);
    }

    public static <T> ApiResult<T> of(ResultCode resultCode, T data) {
        return of(resultCode.getCode(), resultCode.getMsg(), data);
    }

    public static <T> ApiResult<T> of(int code, String msg, T data) {
        ApiResult<T> result = new ApiResult<>();
        result.code = code;
        result.msg = msg;
        result.data = data;
        result.timestamp = System.currentTimeMillis();
        return result;
    }
}
