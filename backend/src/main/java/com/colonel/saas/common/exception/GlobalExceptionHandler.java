package com.colonel.saas.common.exception;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.douyin.DouyinApiException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * <p>通过 {@code @RestControllerAdvice} 统一拦截所有未被捕获的异常，
 * 将其转换为前端可识别的 {@link ApiResult} 格式返回。
 * 确保任何异常情况下，前端都能收到结构化的 JSON 响应，而非 Spring 默认的错误页面。</p>
 *
 * <h3>异常处理优先级（由高到低）</h3>
 * <ol>
 *   <li>{@code MybatisPlusException} — ORM 框架异常，含乐观锁冲突检测</li>
 *   <li>{@code BusinessException} — 业务规则异常，携带业务状态码</li>
 *   <li>{@code DouyinApiException} — 抖音开放平台 API 调用异常</li>
 *   <li>{@code ForbiddenException} — 无权限异常（403）</li>
 *   <li>参数校验异常组（ValidateException、MethodArgumentNotValidException 等）</li>
 *   <li>HTTP 协议异常（方法不支持、媒体类型不支持、资源不存在）</li>
 *   <li>{@code Exception} — 兜底捕获，记录错误日志并返回通用服务器异常</li>
 * </ol>
 *
 * <h3>安全原则</h3>
 * <ul>
 *   <li>业务异常（BusinessException）返回 HTTP 200，通过 code 字段区分错误类型</li>
 *   <li>未授权异常（401）返回 HTTP 401 状态码，触发前端重新登录流程</li>
 *   <li>系统异常不暴露堆栈信息，仅返回通用的"服务器异常"提示</li>
 *   <li>详细的错误上下文仅记录在服务端日志中</li>
 * </ul>
 *
 * @see BusinessException 业务异常基类
 * @see ResultCode 业务状态码定义
 * @see ApiResult 统一响应结构
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 MyBatis-Plus 框架异常，含乐观锁冲突检测。
     *
     * <p>当乐观锁更新失败时，MyBatis-Plus 会抛出异常。
     * 此方法通过关键词匹配识别乐观锁冲突，返回 409 状态码；
     * 其他 ORM 异常视为系统错误，返回 500。</p>
     *
     * @param e MyBatis-Plus 异常
     * @return 统一响应：乐观锁冲突返回 409，其他返回 500
     */
    @ExceptionHandler(MybatisPlusException.class)
    public ApiResult<Void> handleMybatisPlus(MybatisPlusException e) {
        if (isOptimisticLockFailure(e)) {
            return ApiResult.of(ResultCode.CONFLICT.getCode(), "数据已被他人修改，请刷新后重试", null);
        }
        log.error("MyBatis-Plus 异常", e);
        return ApiResult.of(ResultCode.SERVER_ERROR, null);
    }

    /**
     * 处理业务异常。
     *
     * <p>携带业务状态码的异常，大多数情况返回 HTTP 200（前端通过 code 判断），
     * 但未授权（401）场景返回 HTTP 401 状态码以触发前端重新登录。</p>
     *
     * @param e 业务异常
     * @return 包含业务状态码和错误消息的响应实体
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException e) {
        // 未授权场景返回 HTTP 401，触发前端重新登录流程
        HttpStatus status = e.getCode() == ResultCode.UNAUTHORIZED.getCode()
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResult.of(e.getCode(), e.getMessage(), null));
    }

    /**
     * 处理抖音开放平台 API 调用异常。
     *
     * <p>将抖音接口的错误码和错误信息格式化为统一的业务失败响应，
     * 方便前端展示"抖店接口错误"相关的用户提示。</p>
     *
     * @param e 抖音 API 异常，包含 errorCode 和 errorMsg
     * @return 业务失败响应（状态码 460）
     */
    @ExceptionHandler(DouyinApiException.class)
    public ApiResult<Void> handleDouyinApi(DouyinApiException e) {
        String msg = String.format("抖店接口错误[%s]: %s", e.getErrorCode(), e.getErrorMsg());
        return ApiResult.fail(msg);
    }

    /**
     * 处理无权限异常（403 Forbidden）。
     *
     * @param e 无权限异常
     * @return 状态码 403 的统一响应
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResult<Void>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResult.of(ResultCode.FORBIDDEN.getCode(), e.getMessage(), null));
    }

    /**
     * 处理参数校验异常组。
     *
     * <p>统一处理四种参数校验异常来源：</p>
     * <ul>
     *   <li>{@link ValidateException} — 业务层手动校验</li>
     *   <li>{@link MethodArgumentNotValidException} — {@code @Valid} 注解触发</li>
     *   <li>{@link BindException} — 表单绑定失败</li>
     *   <li>{@link ConstraintViolationException} — {@code @Validated} 方法参数校验</li>
     * </ul>
     *
     * <p>优先提取具体的字段级错误消息，回退到通用"参数校验失败"提示。</p>
     *
     * @param e 校验异常
     * @return 状态码 400 的统一响应，携带具体的校验失败描述
     */
    @ExceptionHandler({ValidateException.class, MethodArgumentNotValidException.class,
            BindException.class, ConstraintViolationException.class})
    public ApiResult<Void> handleValidate(Exception e) {
        String msg = "参数校验失败";
        // 从 @Valid 注解触发的异常中提取首个字段级错误消息
        if (e instanceof MethodArgumentNotValidException ex && ex.getBindingResult().getFieldError() != null) {
            msg = ex.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof BindException ex && ex.getBindingResult().getFieldError() != null) {
            // 从表单绑定异常中提取首个字段级错误消息
            msg = ex.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof ConstraintViolationException ex) {
            // 从方法参数校验异常中提取首个违规消息
            var violations = ex.getConstraintViolations();
            if (!violations.isEmpty()) {
                msg = violations.iterator().next().getMessage();
            }
        } else if (e.getMessage() != null && !e.getMessage().isBlank()) {
            // 回退到异常自身消息（如 ValidateException）
            msg = e.getMessage();
        }
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), msg, null);
    }

    /**
     * 处理请求参数类型不匹配异常。
     *
     * <p>当 URL 路径变量或查询参数的类型转换失败时触发，
     * 如期望 UUID 但传入了非法字符串。</p>
     *
     * @param e 类型不匹配异常
     * @return 状态码 400 的统一响应，提示具体参数名格式不正确
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResult<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String name = e.getName() == null ? "参数" : e.getName();
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), name + " 格式不正确", null);
    }

    /**
     * 处理 HTTP 请求方法不支持异常。
     *
     * <p>如对只支持 GET 的接口发送 POST 请求。</p>
     *
     * @param e 方法不支持异常
     * @return 状态码 400 的统一响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResult<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), "不支持的请求方法: " + e.getMethod(), null);
    }

    /**
     * 处理 HTTP 请求媒体类型不支持异常。
     *
     * <p>如发送 JSON 请求但接口只接受 form-data 格式。</p>
     *
     * @param e 媒体类型不支持异常
     * @return 状态码 400 的统一响应
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ApiResult<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), "不支持的 Content-Type: " + e.getContentType(), null);
    }

    /**
     * 处理资源不存在异常（404）。
     *
     * <p>当请求的 API 路径不存在时触发。
     * 同时记录调试日志以便排查路由配置问题。</p>
     *
     * @param e 资源不存在异常
     * @return 状态码 400 的统一响应，提示接口路径
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResult<Void> handleNoResourceFound(NoResourceFoundException e) {
        // #region agent log
        com.colonel.saas.debug.DebugSessionLog.write(
                "H2",
                "GlobalExceptionHandler.handleNoResourceFound",
                "missing api route treated as static resource",
                java.util.Map.of("resourcePath", String.valueOf(e.getResourcePath())));
        // #endregion
        log.warn("接口不存在: {}", e.getResourcePath());
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), "接口不存在: " + e.getResourcePath(), null);
    }

    /**
     * 兜底异常处理器，捕获所有未被其他处理器拦截的异常。
     *
     * <p>记录完整的异常堆栈到服务端日志（便于排查问题），
     * 但仅返回通用的"服务器异常"提示给前端（不暴露内部信息）。</p>
     *
     * @param e 未捕获的异常
     * @return 状态码 500 的统一响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleGeneral(Exception e) {
        log.error("系统异常", e);
        return ApiResult.of(ResultCode.SERVER_ERROR, null);
    }

    /**
     * 判断 MyBatis-Plus 异常是否为乐观锁冲突导致。
     *
     * <p>通过匹配异常消息中的关键词（"乐观锁"、"optimistic"、"version"、"modified"）
     * 来识别乐观锁冲突。这是因为 MyBatis-Plus 未提供专门的乐观锁异常类型，
     * 需要从消息文本中判断。</p>
     *
     * @param e 待检测的异常
     * @return 如果是乐观锁冲突返回 true，否则返回 false
     */
    private static boolean isOptimisticLockFailure(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("乐观锁")
                || lower.contains("optimistic")
                || lower.contains("version")
                || lower.contains("modified");
    }
}
