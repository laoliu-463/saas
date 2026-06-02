package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.UpstreamErrorCode;
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
     * 错误码（机器可读），对应 {@link UpstreamErrorCode} 枚举名。
     *
     * <p>默认 {@link UpstreamErrorCode#UPSTREAM_SERVICE_ERROR}；特定业务码会自动归类：
     * 401/4197 → {@link UpstreamErrorCode#DOUYIN_TOKEN_INVALID}；
     * 429 → {@link UpstreamErrorCode#UPSTREAM_RATE_LIMIT}；
     * 20000/256 → {@link UpstreamErrorCode#UPSTREAM_SERVICE_ERROR}（"服务打瞌睡"）。</p>
     */
    private final String errorCodeTag;

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
        this(errorCode, errorMsg, subCode, logId, endpoint, classifyErrorCode(errorCode, subCode));
    }

    /**
     * 构造 API 异常（带显式 errorCode 标签）。
     *
     * @param errorCode    抖音业务错误码
     * @param errorMsg     错误消息
     * @param subCode      子错误码（可为 null）
     * @param logId        上游日志追踪 ID（可为 null）
     * @param endpoint     发生错误的 API 端点（可为 null）
     * @param errorCodeTag 显式错误码标签（{@link UpstreamErrorCode} 枚举名），不可为 null
     */
    public DouyinApiException(int errorCode, String errorMsg, String subCode, String logId, String endpoint, String errorCodeTag) {
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
        this.errorCodeTag = errorCodeTag == null ? UpstreamErrorCode.UPSTREAM_SERVICE_ERROR.name() : errorCodeTag;
    }

    /**
     * 根据抖音业务码和子错误码推断 UpstreamErrorCode 分类。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>401 / 4197 → {@code DOUYIN_TOKEN_INVALID}（未授权）</li>
     *   <li>429 → {@code UPSTREAM_RATE_LIMIT}（限流）</li>
     *   <li>20000/256 → {@code UPSTREAM_SERVICE_ERROR}（服务打瞌睡）</li>
     *   <li>其他 → {@code UPSTREAM_SERVICE_ERROR}（默认归类）</li>
     * </ul>
     */
    private static String classifyErrorCode(int code, String subCode) {
        if (code == 401 || (subCode != null && subCode.contains("4197"))) {
            return UpstreamErrorCode.DOUYIN_TOKEN_INVALID.name();
        }
        if (code == 429) {
            return UpstreamErrorCode.UPSTREAM_RATE_LIMIT.name();
        }
        return UpstreamErrorCode.UPSTREAM_SERVICE_ERROR.name();
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

    /**
     * 获取错误码标签（机器可读）。
     *
     * @return 错误码（{@link UpstreamErrorCode} 枚举名）
     */
    public String getErrorCodeTag() {
        return errorCodeTag;
    }
}
