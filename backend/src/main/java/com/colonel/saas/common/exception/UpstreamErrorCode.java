package com.colonel.saas.common.exception;

/**
 * 上游 / 远端依赖错误码枚举（用于前端按错误码分支提示）。
 *
 * <p>本枚举为前端提供稳定的、可机读的失败原因标识，与 {@link BusinessException#getErrorCode()}
 * 透传到 {@link com.colonel.saas.common.result.ApiResult#getErrorCode()} 字段配合使用。
 * 当 {@code BusinessException.code} 为通用业务失败码（如 460 / 470）时，
 * {@code errorCode} 进一步细粒度说明失败原因，前端可按 errorCode 提示用户可执行的下一步。</p>
 *
 * <h3>错误码使用约定</h3>
 * <ul>
 *   <li>{@link #DOUYIN_TIMEOUT} — 抖音接口调用超时（连接 / 读取），需要降级或重试</li>
 *   <li>{@link #DOUYIN_TOKEN_INVALID} — 抖音 access_token 无效或缺失，触发重新授权</li>
 *   <li>{@link #UPSTREAM_RATE_LIMIT} — 上游限流（429 / 频控），需要退避重试</li>
 *   <li>{@link #UPSTREAM_SERVICE_ERROR} — 上游服务异常（如 20000/256 业务码"服务打瞌睡"）</li>
 *   <li>{@link #UPSTREAM_BAD_RESPONSE} — 上游返回非预期数据（解析失败、字段缺失）</li>
 *   <li>{@link #LOCAL_QUERY_TIMEOUT} — 本地 DB / 缓存查询超时</li>
 *   <li>{@link #DATA_NOT_READY} — 数据未就绪（如活动未同步过），引导用户先触发同步</li>
 *   <li>{@link #EXTERNAL_GENERIC} — 其他未分类外部服务错误</li>
 * </ul>
 *
 * @see BusinessException#upstream(UpstreamErrorCode, String)
 * @see com.colonel.saas.common.result.ApiResult#getErrorCode()
 */
public enum UpstreamErrorCode {
    /** 抖音接口调用超时（连接超时 / 读取超时） */
    DOUYIN_TIMEOUT,
    /** 抖音 Token 无效或缺失 */
    DOUYIN_TOKEN_INVALID,
    /** 上游限流（429 / 频控） */
    UPSTREAM_RATE_LIMIT,
    /** 上游服务异常（业务码 20000/256 等"服务打瞌睡"） */
    UPSTREAM_SERVICE_ERROR,
    /** 上游返回非预期数据 */
    UPSTREAM_BAD_RESPONSE,
    /** 本地查询超时 */
    LOCAL_QUERY_TIMEOUT,
    /** 数据未就绪，需先触发异步同步 */
    DATA_NOT_READY,
    /** 其他未分类外部服务错误 */
    EXTERNAL_GENERIC
}
