package com.colonel.saas.common.result;

/**
 * 业务状态码枚举。
 *
 * <p>定义系统中所有业务响应的状态码和默认提示消息。前端通过 {@code code} 字段判断响应类型，
 * 后端通过 {@link ApiResult#of(ResultCode, Object)} 或 {@link com.colonel.saas.common.exception.BusinessException}
 * 使用这些状态码。</p>
 *
 * <h3>状态码分段</h3>
 * <table border="1">
 *   <tr><th>范围</th><th>含义</th><th>HTTP 状态码</th></tr>
 *   <tr><td>200</td><td>操作成功</td><td>200</td></tr>
 *   <tr><td>400~404</td><td>客户端错误（参数、认证、权限、资源）</td><td>对应标准 HTTP 码</td></tr>
 *   <tr><td>409</td><td>数据冲突（乐观锁）</td><td>409</td></tr>
 *   <tr><td>460~470</td><td>业务层错误（扩展业务状态码）</td><td>200</td></tr>
 *   <tr><td>500</td><td>服务器内部异常</td><td>500</td></tr>
 * </table>
 *
 * <h3>460~470 扩展业务状态码说明</h3>
 * <ul>
 *   <li>{@link #BUSINESS_ERROR}（460）— 通用业务异常，如规则校验不通过</li>
 *   <li>{@link #STATE_INVALID}（461）— 实体状态不允许当前操作（如已关闭的订单不可修改）</li>
 *   <li>{@link #DUPLICATE}（462）— 重复操作（如重复提交、已存在相同记录）</li>
 *   <li>{@link #IDEMPOTENCY_IN_PROGRESS}（463）— 幂等请求正在处理中，避免重复执行</li>
 *   <li>{@link #EXTERNAL_SERVICE}（470）— 外部服务调用失败（如抖音开放平台 API）</li>
 * </ul>
 *
 * @see ApiResult 统一响应结构
 * @see com.colonel.saas.common.exception.BusinessException 业务异常基类
 * @see com.colonel.saas.common.exception.GlobalExceptionHandler 全局异常处理器
 */
public enum ResultCode {

    /** 操作成功（200） */
    SUCCESS(200, "操作成功"),

    /** 参数校验错误（400），前端应展示具体的校验失败信息 */
    PARAM_ERROR(400, "参数错误"),

    /** 未授权（401），前端收到后应跳转至登录页 */
    UNAUTHORIZED(401, "未授权"),

    /** 无权限（403），当前用户无权访问目标资源 */
    FORBIDDEN(403, "无权限"),

    /** 资源不存在（404），请求的实体或接口路径不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 数据冲突（409），乐观锁更新失败或并发修改冲突 */
    CONFLICT(409, "数据冲突"),

    /** 通用业务异常（460），业务规则校验不通过 */
    BUSINESS_ERROR(460, "业务异常"),

    /** 实体状态不允许当前操作（461），如审批流程中状态不匹配 */
    STATE_INVALID(461, "状态不允许"),

    /** 重复操作（462），相同数据已存在或请求已处理 */
    DUPLICATE(462, "重复操作"),

    /** 幂等请求处理中（463），相同请求正在执行，避免重复提交 */
    IDEMPOTENCY_IN_PROGRESS(463, "请求处理中"),

    /** 外部服务异常（470），抖音开放平台等第三方 API 调用失败 */
    EXTERNAL_SERVICE(470, "外部服务异常"),

    /** 服务器内部异常（500），未预期的系统错误 */
    SERVER_ERROR(500, "服务器异常"),

    /** 服务暂时不可用（503），依赖的权威事实当前不可取得 */
    SERVICE_UNAVAILABLE(503, "服务暂时不可用");

    /** 业务状态码（前端通过此值判断响应类型） */
    private final int code;

    /** 默认提示消息（前端可直接展示，也可由后端传入更精确的消息覆盖） */
    private final String msg;

    /**
     * 构造业务状态码枚举值。
     *
     * @param code 业务状态码
     * @param msg  默认提示消息
     */
    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 获取业务状态码。
     *
     * @return 业务状态码整数值
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取默认提示消息。
     *
     * @return 用户可读的默认提示消息
     */
    public String getMsg() {
        return msg;
    }
}
