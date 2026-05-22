package com.colonel.saas.security;

import com.colonel.saas.controller.SysConfigController;
import com.colonel.saas.entity.OperationLog;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OperationLogInterceptorTest {

    @Mock
    private OperationLogService operationLogService;

    @Test
    void afterCompletion_recordsMutatingRequest() throws Exception {
        OperationLogInterceptor interceptor = new OperationLogInterceptor(operationLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/configs/" + UUID.randomUUID());
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute("userId", UUID.randomUUID());
        request.setAttribute("username", "admin");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "cfg-1"));
        request.addParameter("configGroup", "sample");
        interceptor.preHandle(request, response, new Object());

        Method method = SysConfigController.class.getMethod("update", UUID.class, SystemConfig.class, UUID.class);
        HandlerMethod handlerMethod = new HandlerMethod(new SysConfigController(null), method);

        interceptor.afterCompletion(request, response, handlerMethod, null);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).record(captor.capture());
        OperationLog log = captor.getValue();
        assertThat(log.getUsername()).isEqualTo("admin");
        assertThat(log.getRequestMethod()).isEqualTo("PUT");
        assertThat(log.getModule()).isEqualTo("系统配置");
        assertThat(log.getAction()).isEqualTo("更新配置");
        assertThat(log.getTargetId()).isEqualTo("cfg-1");
        assertThat(log.getRequestParams()).containsEntry("configGroup", "sample");
    }

    @Test
    void afterCompletion_skipsGetRequest() throws Exception {
        OperationLogInterceptor interceptor = new OperationLogInterceptor(operationLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/configs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());

        Method method = SysConfigController.class.getMethod("page", String.class, String.class, int.class, int.class);
        HandlerMethod handlerMethod = new HandlerMethod(new SysConfigController(null), method);

        interceptor.afterCompletion(request, response, handlerMethod, null);

        verify(operationLogService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void afterCompletion_recordsFallbackMetadataForPlainHandler() {
        OperationLogInterceptor interceptor = new OperationLogInterceptor(operationLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test/path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);
        request.setAttribute("username", "operator");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2");
        request.addHeader("User-Agent", "JUnit");
        request.addParameter("keyword", "达人");
        request.addParameter("tag", "a", "b");

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).record(captor.capture());
        OperationLog log = captor.getValue();
        assertThat(log.getUsername()).isEqualTo("operator");
        assertThat(log.getRequestMethod()).isEqualTo("POST");
        assertThat(log.getRequestUrl()).isEqualTo("/api/test/path");
        assertThat(log.getResponseCode()).isEqualTo("201");
        assertThat(log.getIpAddress()).isEqualTo("1.1.1.1");
        assertThat(log.getUserAgent()).isEqualTo("JUnit");
        assertThat(log.getDurationMs()).isZero();
        assertThat(log.getErrorMessage()).isEqualTo("boom");
        assertThat(log.getModule()).isEqualTo("api");
        assertThat(log.getAction()).isEqualTo("POST /api/test/path");
        assertThat(log.getTargetType()).isEqualTo("api");
        assertThat(log.getTargetName()).isEqualTo("/api/test/path");
        assertThat(log.getContent()).isEqualTo("/api/test/path");
        assertThat(log.getRequestParams()).containsEntry("keyword", "达人");
        assertThat((String[]) log.getRequestParams().get("tag")).containsExactly("a", "b");
    }

    @Test
    void afterCompletion_swallowsOperationLogFailures() {
        OperationLogInterceptor interceptor = new OperationLogInterceptor(operationLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/configs/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new IllegalStateException("log db unavailable"))
                .when(operationLogService)
                .record(org.mockito.ArgumentMatchers.any());

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(operationLogService).record(org.mockito.ArgumentMatchers.any());
    }
}
