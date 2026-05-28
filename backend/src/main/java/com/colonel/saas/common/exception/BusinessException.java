package com.colonel.saas.common.exception;

import com.colonel.saas.common.result.ResultCode;

/**
 * 业务异常基类，用于表示业务规则不满足时的预期异常。
 *
 * <p>与系统异常（如 NullPointerException、数据库连接失败等）不同，
 * {@code BusinessException} 代表的是可预见的业务规则违反，
 * 如参数校验失败、资源不存在、状态不允许操作等。</p>
 *
 * <h3>异常传播机制</h3>
 * <p>抛出的 {@code BusinessException} 会被
 * {@link GlobalExceptionHandler} 捕获并转换为统一的 {@link com.colonel.saas.common.result.ApiResult} 格式，
 * 前端通过 {@code code} 字段判断异常类型并展示对应的用户提示。</p>
 *
 * <h3>推荐用法（静态工厂方法）</h3>
 * <pre>{@code
 * // 参数校验失败（400）
 * throw BusinessException.param("手机号格式不正确");
 *
 * // 资源不存在（404）
 * throw BusinessException.notFound("达人记录不存在: " + id);
 *
 * // 数据冲突（409，通常配合乐观锁使用）
 * throw BusinessException.conflict("数据已被他人修改，请刷新后重试");
 *
 * // 状态不允许（461）
 * throw BusinessException.stateInvalid("已审核的商品不能修改");
 *
 * // 外部服务异常（470）
 * throw BusinessException.external("抖店接口调用失败", cause);
 * }</pre>
 *
 * <h3>语义化工厂方法</h3>
 * <p>推荐使用静态工厂方法而非直接构造器，因为工厂方法名称直接表达了
 * 异常的业务语义（param、notFound、conflict 等），同时自动关联正确的 {@link ResultCode}。</p>
 *
 * @see ResultCode 业务状态码定义
 * @see GlobalExceptionHandler 全局异常处理器，将此异常转换为 API 响应
 * @see ForbiddenException 无权限异常（HTTP 403）
 * @see ValidateException 参数校验异常
 */
public class BusinessException extends RuntimeException {

    /** 业务状态码，对应 {@link ResultCode} 中定义的数字编码 */
    private final int code;

    /**
     * 根据业务状态码构造异常。
     *
     * @param resultCode 业务状态码枚举，决定 HTTP 响应中的 code 值
     * @param message    异常描述信息，将作为 msg 返回给前端
     */
    public BusinessException(ResultCode resultCode, String message) {
        this(resultCode.getCode(), message);
    }

    /**
     * 根据数字状态码构造异常。
     *
     * @param code    业务状态码数字值
     * @param message 异常描述信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 根据数字状态码和原因构造异常。
     *
     * @param code    业务状态码数字值
     * @param message 异常描述信息
     * @param cause   原始异常，用于保留异常链（便于日志追踪根因）
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 根据业务状态码构造异常的通用工厂方法。
     *
     * @param resultCode 业务状态码枚举
     * @param message    异常描述信息
     * @return 新的 BusinessException 实例
     */
    public static BusinessException of(ResultCode resultCode, String message) {
        return new BusinessException(resultCode, message);
    }

    /**
     * 通用业务失败（状态码 460）。
     *
     * <p>用于未归入更细语义码的业务规则违反场景。
     * 如果有更具体的语义码（如 notFound、conflict 等），请优先使用对应方法。</p>
     *
     * @param message 业务失败描述
     * @return 新的 BusinessException 实例
     */
    public static BusinessException business(String message) {
        return new BusinessException(ResultCode.BUSINESS_ERROR, message);
    }

    /**
     * 通用业务失败（状态码 460），带原始异常。
     *
     * @param message 业务失败描述
     * @param cause   原始异常
     * @return 新的 BusinessException 实例
     */
    public static BusinessException business(String message, Throwable cause) {
        return new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), message, cause);
    }

    /**
     * 参数校验失败（状态码 400）。
     *
     * <p>用于请求参数不合法的场景，如格式错误、超出范围、缺少必填项等。</p>
     *
     * @param message 参数错误描述
     * @return 新的 BusinessException 实例
     */
    public static BusinessException param(String message) {
        return new BusinessException(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 参数校验失败（状态码 400），带原始异常。
     *
     * @param message 参数错误描述
     * @param cause   原始异常
     * @return 新的 BusinessException 实例
     */
    public static BusinessException param(String message, Throwable cause) {
        return new BusinessException(ResultCode.PARAM_ERROR.getCode(), message, cause);
    }

    /**
     * 资源不存在（状态码 404）。
     *
     * <p>用于查询或操作的目标资源未找到的场景。</p>
     *
     * @param message 资源不存在的描述，建议包含资源标识（如 ID）
     * @return 新的 BusinessException 实例
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(ResultCode.NOT_FOUND, message);
    }

    /**
     * 数据冲突（状态码 409）。
     *
     * <p>通常配合乐观锁使用，当并发更新检测到版本不一致时抛出。
     * 也用于其他数据冲突场景，如外键约束违反等。</p>
     *
     * @param message 冲突描述
     * @return 新的 BusinessException 实例
     */
    public static BusinessException conflict(String message) {
        return new BusinessException(ResultCode.CONFLICT, message);
    }

    /**
     * 状态不允许（状态码 461）。
     *
     * <p>用于当前实体状态不满足操作前提的场景，
     * 如对已审核的商品执行删除、对已结束的合作执行跟进等。</p>
     *
     * @param message 状态不允许的描述
     * @return 新的 BusinessException 实例
     */
    public static BusinessException stateInvalid(String message) {
        return new BusinessException(ResultCode.STATE_INVALID, message);
    }

    /**
     * 重复操作（状态码 462）。
     *
     * <p>用于标识幂等性检查失败的场景，如同一请求被重复提交。</p>
     *
     * @param message 重复操作描述
     * @return 新的 BusinessException 实例
     */
    public static BusinessException duplicate(String message) {
        return new BusinessException(ResultCode.DUPLICATE, message);
    }

    /**
     * 请求处理中（状态码 463）。
     *
     * <p>用于幂等性保护场景：相同请求的第一次调用正在处理中，
     * 后续重复调用返回此状态，前端应提示用户稍后重试。</p>
     *
     * @param message 处理中描述
     * @return 新的 BusinessException 实例
     */
    public static BusinessException idempotencyInProgress(String message) {
        return new BusinessException(ResultCode.IDEMPOTENCY_IN_PROGRESS, message);
    }

    /**
     * 无权限（状态码 403）。
     *
     * <p>用于认证通过但权限不足的场景。
     * 注意：与 {@link ForbiddenException} 语义相同，但走不同的异常处理分支。</p>
     *
     * @param message 权限不足描述
     * @return 新的 BusinessException 实例
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(ResultCode.FORBIDDEN, message);
    }

    /**
     * 外部服务异常（状态码 470）。
     *
     * <p>用于调用外部 API（如抖音开放平台）失败的场景。
     * 建议在 message 中包含外部服务名称和错误码。</p>
     *
     * @param message 外部服务异常描述
     * @return 新的 BusinessException 实例
     */
    public static BusinessException external(String message) {
        return new BusinessException(ResultCode.EXTERNAL_SERVICE, message);
    }

    /**
     * 外部服务异常（状态码 470），带原始异常。
     *
     * @param message 外部服务异常描述
     * @param cause   原始异常（如 HttpClientErrorException）
     * @return 新的 BusinessException 实例
     */
    public static BusinessException external(String message, Throwable cause) {
        return new BusinessException(ResultCode.EXTERNAL_SERVICE.getCode(), message, cause);
    }

    /**
     * 获取业务状态码。
     *
     * @return 业务状态码数字值，对应 {@link ResultCode} 中的 code
     */
    public int getCode() {
        return code;
    }
}
