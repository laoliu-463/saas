package com.colonel.saas.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.douyin.DouyinApiException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private Level originalLevel;

    @BeforeEach
    void muteHandlerLogger() {
        originalLevel = handlerLogger.getLevel();
        handlerLogger.setLevel(Level.OFF);
    }

    @AfterEach
    void restoreHandlerLogger() {
        handlerLogger.setLevel(originalLevel);
    }

    @Test
    void handleBusiness_returnsFailResult() {
        BusinessException ex = new BusinessException("用户名已存在");
        ApiResult<Void> result = handler.handleBusiness(ex);
        assertThat(result.getCode()).isEqualTo(460);
        assertThat(result.getMsg()).isEqualTo("用户名已存在");
    }

    @Test
    void handleDouyinApi_returnsFailWithErrorCode() {
        DouyinApiException ex = new DouyinApiException(10012, "access token expired", null, null, null);
        ApiResult<Void> result = handler.handleDouyinApi(ex);
        assertThat(result.getCode()).isEqualTo(460);
        assertThat(result.getMsg()).contains("10012");
        assertThat(result.getMsg()).contains("access token expired");
    }

    @Test
    void handleForbidden_returnsForbiddenResult() {
        ForbiddenException ex = new ForbiddenException("无权限访问该资源");
        ApiResult<Void> result = handler.handleForbidden(ex);
        assertThat(result.getCode()).isEqualTo(403);
        assertThat(result.getMsg()).isEqualTo("无权限访问该资源");
    }

    @Test
    void handleValidate_withMethodArgumentNotValid_pullsFieldMessage() throws Exception {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "username", "用户名不能为空");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldError()).thenReturn(fieldError);

        ApiResult<Void> result = handler.handleValidate(ex);
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("用户名不能为空");
    }

    @Test
    void handleValidate_withBindException_pullsFieldMessage() throws Exception {
        BindException ex = mock(BindException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "password", "密码长度不足");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldError()).thenReturn(fieldError);

        ApiResult<Void> result = handler.handleValidate(ex);
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("密码长度不足");
    }

    @Test
    void handleValidate_withConstraintViolation_returnsMessage() {
        ConstraintViolationException ex = mock(ConstraintViolationException.class);
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("必须大于0");
        Set<ConstraintViolation<?>> set = Collections.singleton(violation);
        when(ex.getConstraintViolations()).thenReturn(set);

        ApiResult<Void> result = handler.handleValidate(ex);
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("必须大于0");
    }

    @Test
    void handleValidate_withNoFieldError_usesFallbackMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(mock(BindingResult.class));
        when(ex.getMessage()).thenReturn("custom validation message");

        ApiResult<Void> result = handler.handleValidate(ex);
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("custom validation message");
    }

    @Test
    void handleGeneral_withExceptionMessage_usesMessage() {
        RuntimeException ex = new RuntimeException("连接数据库超时");
        ApiResult<Void> result = handler.handleGeneral(ex);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).contains("连接数据库超时");
    }

    @Test
    void handleGeneral_withNullMessage_usesClassName() {
        RuntimeException ex = new RuntimeException();
        ApiResult<Void> result = handler.handleGeneral(ex);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).contains("RuntimeException");
    }

    @Test
    void handleGeneral_withBlankMessage_usesClassName() {
        RuntimeException ex = new RuntimeException("   ");
        ApiResult<Void> result = handler.handleGeneral(ex);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).contains("RuntimeException");
    }
}
