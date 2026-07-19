package com.colonel.saas.security;

import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.service.OrderSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OperationLogResponseAdviceTest {

    @Test
    void beforeBodyWrite_shouldExposeStructuredBusinessErrorToAuditInterceptor() {
        OperationLogResponseAdvice advice = new OperationLogResponseAdvice();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        ApiResult<Void> body = ApiResult.of(460, "状态不允许", null, "STATE_INVALID");

        advice.beforeBodyWrite(
                body,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                mock(ServerHttpResponse.class));

        assertThat(servletRequest.getAttribute(OperationLogInterceptor.ATTR_ERROR_CODE))
                .isEqualTo("STATE_INVALID");
        assertThat(servletRequest.getAttribute(OperationLogInterceptor.ATTR_ERROR_MESSAGE))
                .isEqualTo("状态不允许");
    }

    @Test
    void beforeBodyWrite_shouldSkipDuplicateHttpAuditForOrderSyncSummary() {
        OperationLogResponseAdvice advice = new OperationLogResponseAdvice();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        OrderSyncService.SyncResult result = new OrderSyncService.SyncResult(
                100L, 200L, 1, 2, 1, 1, 1, 1, 0, false, 2, "NO_MORE");

        advice.beforeBodyWrite(
                ApiResult.ok(result),
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                mock(ServerHttpResponse.class));

        assertThat(servletRequest.getAttribute(OperationLogInterceptor.ATTR_SKIP)).isEqualTo(true);
    }
}
