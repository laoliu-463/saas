package com.colonel.saas.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import com.colonel.saas.common.web.RequestIdContext;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应结构体。
 *
 * <p>所有 REST API 接口的响应均封装为此结构，前端通过 {@code code} 字段判断业务结果，
 * 通过 {@code data} 字段获取实际数据。{@code timestamp} 用于前端时序对齐和日志追踪。</p>
 *
 * <h3>响应约定</h3>
 * <ul>
 *   <li>{@code code = 200} 表示业务成功，{@code data} 携带业务数据</li>
 *   <li>{@code code = 400~470} 表示业务异常，{@code msg} 携带用户可读的错误提示</li>
 *   <li>{@code code = 500} 表示服务器内部异常，{@code msg} 为通用提示</li>
 *   <li>HTTP 状态码通常为 200（业务异常也返回 200，由 code 区分），401 例外（触发前端重新登录）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 成功响应
 * return ApiResult.ok(talentList);
 *
 * // 成功响应（无数据）
 * return ApiResult.ok();
 *
 * // 业务失败响应
 * return ApiResult.fail("达人不存在");
 *
 * // 自定义状态码响应
 * return ApiResult.of(ResultCode.CONFLICT, null);
 * }</pre>
 *
 * @param <T> 响应数据的类型
 * @see ResultCode 业务状态码枚举
 * @see com.colonel.saas.common.exception.GlobalExceptionHandler 全局异常处理器（负责将异常转换为 ApiResult）
 */
@Data
@Schema(description = "统一响应结构")
public class ApiResult<T> implements Serializable {

    /** 业务状态码，参见 {@link ResultCode} 的枚举定义 */
    @Schema(description = "业务状态码", example = "200")
    private int code;

    /** 响应消息，成功时为"操作成功"，失败时为用户可读的错误提示 */
    @Schema(description = "响应消息", example = "操作成功")
    private String msg;

    /** 响应数据，业务失败时通常为 null */
    @Schema(description = "响应数据")
    private T data;

    /**
     * 错误码（字符串，机器可读），用于前端按错误码分支提示。
     *
     * <p>仅在业务失败时填充；成功时为 null。
     * 配合 {@link com.colonel.saas.common.exception.UpstreamErrorCode} 枚举使用，
     * 提供稳定的、可机读的失败原因标识。</p>
     */
    @Schema(description = "错误码（机器可读）", example = "DOUYIN_TIMEOUT")
    private String errorCode;

    /** 请求链路 ID，失败响应必须可据此检索服务端根因日志。 */
    @Schema(description = "请求链路 ID", example = "req-20260718-001")
    private String requestId;

    /** 服务器处理完成时的时间戳（毫秒），用于前端时序对齐和请求链路追踪 */
    @Schema(description = "服务器时间戳（毫秒）", example = "1713628800000")
    private long timestamp;

    /**
     * 构建成功响应（无数据）。
     *
     * <p>适用于不需要返回数据的写操作（如删除、更新确认）。</p>
     *
     * @param <T> 响应数据类型
     * @return code=200、msg="操作成功"、data=null 的响应
     */
    public static <T> ApiResult<T> ok() {
        return ok(null);
    }

    /**
     * 构建成功响应（携带数据）。
     *
     * <p>适用于查询和创建类接口，将业务数据包装为统一响应。</p>
     *
     * @param data 业务数据，可为 null
     * @param <T>  响应数据类型
     * @return code=200、msg="操作成功"、data=传入数据 的响应
     */
    public static <T> ApiResult<T> ok(T data) {
        return of(ResultCode.SUCCESS, data);
    }

    /**
     * 构建业务失败响应。
     *
     * <p>返回 {@link ResultCode#BUSINESS_ERROR}（code=460）状态码，
     * 携带用户可读的错误提示消息。适用于业务规则校验不通过的场景。</p>
     *
     * @param msg 用户可读的错误提示消息
     * @param <T> 响应数据类型
     * @return code=460、msg=传入消息、data=null 的响应
     */
    public static <T> ApiResult<T> fail(String msg) {
        return of(ResultCode.BUSINESS_ERROR.getCode(), msg, null);
    }

    /**
     * 使用预定义的业务状态码构建响应。
     *
     * <p>从 {@link ResultCode} 枚举中提取 code 和 msg，适用于需要精确控制状态码的场景
     * （如参数错误 400、数据冲突 409 等）。</p>
     *
     * @param resultCode 业务状态码枚举值
     * @param data       响应数据，可为 null
     * @param <T>        响应数据类型
     * @return 包含指定状态码和数据的响应
     */
    public static <T> ApiResult<T> of(ResultCode resultCode, T data) {
        return of(resultCode.getCode(), resultCode.getMsg(), data);
    }

    /**
     * 使用自定义 code、msg、data 构建响应（通用工厂方法）。
     *
     * <p>所有静态工厂方法最终都委托到此方法。自动填充 {@link #timestamp} 字段。</p>
     *
     * @param code 业务状态码
     * @param msg  响应消息
     * @param data 响应数据，可为 null
     * @param <T>  响应数据类型
     * @return 完整的统一响应对象
     */
    public static <T> ApiResult<T> of(int code, String msg, T data) {
        return of(code, msg, data, null);
    }

    /**
     * 使用自定义 code、msg、data、errorCode 构建响应（带错误码的通用工厂方法）。
     *
     * <p>在 {@link #of(int, String, Object)} 基础上额外透传 {@link com.colonel.saas.common.exception.UpstreamErrorCode}
     * 字符串值，供前端按错误码分支处理。</p>
     *
     * @param code      业务状态码
     * @param msg       响应消息
     * @param data      响应数据，可为 null
     * @param errorCode 错误码字符串（{@link com.colonel.saas.common.exception.UpstreamErrorCode} 枚举名），可为 null
     * @param <T>       响应数据类型
     * @return 完整的统一响应对象
     */
    public static <T> ApiResult<T> of(int code, String msg, T data, String errorCode) {
        ApiResult<T> result = new ApiResult<>();
        result.code = code;
        result.msg = msg;
        result.data = data;
        result.errorCode = errorCode;
        result.requestId = RequestIdContext.current();
        result.timestamp = System.currentTimeMillis();
        return result;
    }
}
