package com.colonel.saas.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.domain.user.api.AuthorizationUnavailableException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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
        BusinessException ex = BusinessException.duplicate("用户名已存在");
        ResponseEntity<ApiResult<Void>> response = handler.handleBusiness(ex);
        ApiResult<Void> result = response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getCode()).isEqualTo(462);
        assertThat(result.getMsg()).isEqualTo("用户名已存在");
    }

    @Test
    void handleBusiness_semanticCodes_preservedInBody() {
        BusinessException ex = BusinessException.conflict("并发冲突");
        ApiResult<Void> result = handler.handleBusiness(ex).getBody();
        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getMsg()).isEqualTo("并发冲突");
    }

    @Test
    void handleMybatisPlus_optimisticLock_returnsConflict() {
        MybatisPlusException ex = new MybatisPlusException("乐观锁异常,更新失败");
        ApiResult<Void> result = handler.handleMybatisPlus(ex);
        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getMsg()).contains("刷新");
    }

    @Test
    void handleBusiness_withUnauthorizedCode_returns401Status() {
        BusinessException ex = new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        ResponseEntity<ApiResult<Void>> response = handler.handleBusiness(ex);
        ApiResult<Void> result = response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMsg()).isEqualTo("用户名或密码错误");
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
        ResponseEntity<ApiResult<Void>> response = handler.handleForbidden(ex);
        ApiResult<Void> result = response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getCode()).isEqualTo(403);
        assertThat(result.getMsg()).isEqualTo("无权限访问该资源");
    }

    @Test
    void handleAuthorizationUnavailable_returnsSafe503AndLogsOnlyCauseClass() {
        IllegalStateException cause = new IllegalStateException(
                "token=forbidden password=forbidden redis=forbidden");
        AuthorizationUnavailableException ex = new AuthorizationUnavailableException(cause);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.setLevel(Level.WARN);
        handlerLogger.addAppender(appender);
        try {
            ResponseEntity<ApiResult<Void>> response = handler.handleAuthorizationUnavailable(ex);
            ApiResult<Void> result = response.getBody();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(result.getCode()).isEqualTo(503);
            assertThat(result.getMsg()).isEqualTo("授权事实暂时不可用");
            assertThat(result.getErrorCode()).isEqualTo("AUTHORIZATION_UNAVAILABLE");
            assertThat(result.getData()).isNull();
            assertThat(result.getMsg()).doesNotContain(cause.getMessage());
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                        .isEqualTo("授权事实暂时不可用: cause=IllegalStateException")
                        .doesNotContain(cause.getMessage());
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            handlerLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void serviceUnavailableResultCode_is503AndDoesNotConflict() {
        assertThat(ResultCode.SERVICE_UNAVAILABLE.getCode()).isEqualTo(503);
        assertThat(ResultCode.SERVICE_UNAVAILABLE.getMsg()).isEqualTo("服务暂时不可用");
        assertThat(ResultCode.values())
                .extracting(ResultCode::getCode)
                .doesNotHaveDuplicates();
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
    void handleMethodNotSupported_returnsParamErrorWithMethod() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");

        ApiResult<Void> result = handler.handleMethodNotSupported(ex);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("不支持的请求方法: POST");
    }

    @Test
    void handleMediaTypeNotSupported_returnsParamErrorWithContentType() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, Collections.emptyList());

        ApiResult<Void> result = handler.handleMediaTypeNotSupported(ex);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("不支持的 Content-Type: text/plain");
    }

    @Test
    void handleGeneral_withExceptionMessage_returnsGenericMessage() {
        RuntimeException ex = new RuntimeException("连接数据库超时");
        ApiResult<Void> result = handler.handleGeneral(ex);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).isEqualTo("服务器异常");
        assertThat(result.getMsg()).doesNotContain("连接数据库超时");
    }

    @Test
    void handleGeneral_withNullMessage_returnsGenericMessage() {
        RuntimeException ex = new RuntimeException();
        ApiResult<Void> result = handler.handleGeneral(ex);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).isEqualTo("服务器异常");
    }

    @Test
    void handleGeneral_withBlankMessage_returnsGenericMessage() {
        RuntimeException ex = new RuntimeException("   ");
        ApiResult<Void> result = handler.handleGeneral(ex);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).isEqualTo("服务器异常");
    }
}
