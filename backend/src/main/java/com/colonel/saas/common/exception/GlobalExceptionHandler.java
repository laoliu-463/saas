package com.colonel.saas.common.exception;

import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> handleBusiness(BusinessException e) {
        return ApiResult.fail(e.getMessage());
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
        } else if (e.getMessage() != null && !e.getMessage().isBlank()) {
            msg = e.getMessage();
        }
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), msg, null);
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleGeneral(Exception e) {
        log.error("系统异常", e);
        return ApiResult.of(ResultCode.SERVER_ERROR.getCode(), ResultCode.SERVER_ERROR.getMsg(), null);
    }
}
