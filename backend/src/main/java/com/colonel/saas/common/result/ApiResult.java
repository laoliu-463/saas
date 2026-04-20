package com.colonel.saas.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "统一响应结构")
public class ApiResult<T> implements Serializable {
    @Schema(description = "业务状态码", example = "200")
    private int code;
    @Schema(description = "响应消息", example = "操作成功")
    private String msg;
    @Schema(description = "响应数据")
    private T data;
    @Schema(description = "服务器时间戳（毫秒）", example = "1713628800000")
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
