package com.colonel.saas.douyin;

import lombok.Getter;

/**
 * 抖音开放平台 API 业务异常。
 * <p>
 * 当抖音 API 返回非成功响应时抛出，携带完整的错误上下文信息，
 * 包括错误码、子错误码、日志 ID 和调用端点。
 *
 * <ul>
 *   <li>错误码封装 — 保存抖音 API 返回的 err_no / code</li>
 *   <li>子错误码 — 保存 sub_code 用于精细化错误处理</li>
 *   <li>日志追踪 — 保存 log_id 用于问题排查</li>
 *   <li>端点标识 — 记录发生错误的 API 端点</li>
 * </ul>
 *
 * 所属业务领域：抖音开放平台 / 异常处理
 *
 * @see DouyinApiClient
 */
@Getter
public class DouyinApiException extends RuntimeException {

    private final int errorCode;
    private final String errorMsg;
    private final String subCode;
    private final String logId;
    private final String endpoint;

    /**
     * 构造 API 异常（简略参数）。
     * <p>
     * subCode、logId、endpoint 均设为 null，委托至完整构造方法。
     *
     * @param errorCode 业务错误码
     * @param errorMsg  错误消息
     */
    public DouyinApiException(int errorCode, String errorMsg) {
        this(errorCode, errorMsg, null, null, null);
    }

    /**
     * 构造 API 异常（完整参数）。
     * <p>
     * 同时设置所有错误上下文信息，异常 message 由各字段拼接而成。
     *
     * @param errorCode 业务错误码
     * @param errorMsg  错误消息
     * @param subCode   子错误码（可为 null）
     * @param logId     上游日志追踪 ID（可为 null）
     * @param endpoint  发生错误的 API 端点（可为 null）
     */
    public DouyinApiException(int errorCode, String errorMsg, String subCode, String logId, String endpoint) {
        // 第一步：拼接异常 message，包含所有错误上下文字段
        super("Douyin API error: code=" + errorCode
                + ", subCode=" + (subCode == null ? "" : subCode)
                + ", logId=" + (logId == null ? "" : logId)
                + ", endpoint=" + (endpoint == null ? "" : endpoint)
                + ", msg=" + errorMsg);
        // 第二步：保存各字段供调用方按需获取
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
        this.subCode = subCode;
        this.logId = logId;
        this.endpoint = endpoint;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public String getSubCode() {
        return subCode;
    }

    public String getLogId() {
        return logId;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
