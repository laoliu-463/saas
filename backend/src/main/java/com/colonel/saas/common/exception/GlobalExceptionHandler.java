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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MybatisPlusException.class)
    public ApiResult<Void> handleMybatisPlus(MybatisPlusException e) {
        if (isOptimisticLockFailure(e)) {
            return ApiResult.of(ResultCode.CONFLICT.getCode(), "数据已被他人修改，请刷新后重试", null);
        }
        log.error("MyBatis-Plus 异常", e);
        return ApiResult.of(ResultCode.SERVER_ERROR, null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = e.getCode() == ResultCode.UNAUTHORIZED.getCode()
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResult.of(e.getCode(), e.getMessage(), null));
    }

    @ExceptionHandler(DouyinApiException.class)
    public ApiResult<Void> handleDouyinApi(DouyinApiException e) {
        String msg = String.format("抖店接口错误[%s]: %s", e.getErrorCode(), e.getErrorMsg());
        return ApiResult.fail(msg);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ApiResult<Void> handleForbidden(ForbiddenException e) {
        return ApiResult.of(ResultCode.FORBIDDEN.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler({ValidateException.class, MethodArgumentNotValidException.class,
            BindException.class, ConstraintViolationException.class})
    public ApiResult<Void> handleValidate(Exception e) {
        String msg = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException ex && ex.getBindingResult().getFieldError() != null) {
            msg = ex.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof BindException ex && ex.getBindingResult().getFieldError() != null) {
            msg = ex.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof ConstraintViolationException ex) {
            var violations = ex.getConstraintViolations();
            if (!violations.isEmpty()) {
                msg = violations.iterator().next().getMessage();
            }
        } else if (e.getMessage() != null && !e.getMessage().isBlank()) {
            msg = e.getMessage();
        }
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), msg, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResult<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String name = e.getName() == null ? "参数" : e.getName();
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), name + " 格式不正确", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResult<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), "不支持的请求方法: " + e.getMethod(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ApiResult<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), "不支持的 Content-Type: " + e.getContentType(), null);
    }

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

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleGeneral(Exception e) {
        log.error("系统异常", e);
        return ApiResult.of(ResultCode.SERVER_ERROR, null);
    }

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
