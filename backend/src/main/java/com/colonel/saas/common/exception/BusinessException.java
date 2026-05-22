package com.colonel.saas.common.exception;

import com.colonel.saas.common.result.ResultCode;

public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * @deprecated 请使用 {@link #param(String)}、{@link #duplicate(String)} 等语义工厂；
     *             未分类消息请用 {@link #business(String)} 显式返回 460。
     */
    @Deprecated
    public BusinessException(String message) {
        this(ResultCode.PARAM_ERROR, message);
    }

    public BusinessException(ResultCode resultCode, String message) {
        this(resultCode.getCode(), message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * @deprecated 请使用带 {@link ResultCode} 的构造器或静态工厂。
     */
    @Deprecated
    public BusinessException(String message, Throwable cause) {
        this(ResultCode.PARAM_ERROR.getCode(), message, cause);
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static BusinessException of(ResultCode resultCode, String message) {
        return new BusinessException(resultCode, message);
    }

    /** 未归入更细语义码时的通用业务失败（460）。 */
    public static BusinessException business(String message) {
        return new BusinessException(ResultCode.BUSINESS_ERROR, message);
    }

    public static BusinessException business(String message, Throwable cause) {
        return new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), message, cause);
    }

    public static BusinessException param(String message) {
        return new BusinessException(ResultCode.PARAM_ERROR, message);
    }

    public static BusinessException param(String message, Throwable cause) {
        return new BusinessException(ResultCode.PARAM_ERROR.getCode(), message, cause);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ResultCode.NOT_FOUND, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(ResultCode.CONFLICT, message);
    }

    public static BusinessException stateInvalid(String message) {
        return new BusinessException(ResultCode.STATE_INVALID, message);
    }

    public static BusinessException duplicate(String message) {
        return new BusinessException(ResultCode.DUPLICATE, message);
    }

    public static BusinessException idempotencyInProgress(String message) {
        return new BusinessException(ResultCode.IDEMPOTENCY_IN_PROGRESS, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ResultCode.FORBIDDEN, message);
    }

    public static BusinessException external(String message) {
        return new BusinessException(ResultCode.EXTERNAL_SERVICE, message);
    }

    public static BusinessException external(String message, Throwable cause) {
        return new BusinessException(ResultCode.EXTERNAL_SERVICE.getCode(), message, cause);
    }

    public int getCode() {
        return code;
    }
}
