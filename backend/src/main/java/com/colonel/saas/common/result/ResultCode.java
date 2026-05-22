package com.colonel.saas.common.result;

public enum ResultCode {
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    BUSINESS_ERROR(460, "业务异常"),
    STATE_INVALID(461, "状态不允许"),
    DUPLICATE(462, "重复操作"),
    IDEMPOTENCY_IN_PROGRESS(463, "请求处理中"),
    EXTERNAL_SERVICE(470, "外部服务异常"),
    SERVER_ERROR(500, "服务器异常");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
