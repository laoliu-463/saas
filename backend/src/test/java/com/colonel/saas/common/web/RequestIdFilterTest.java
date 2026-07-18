package com.colonel.saas.common.web;

import com.colonel.saas.common.result.ApiResult;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldEchoSafeIncomingRequestIdAndExposeItToApiResult() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdContext.HEADER, "req-20260718-001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        new RequestIdFilter().doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdContext.HEADER)).isEqualTo("req-20260718-001");
        assertThat(request.getAttribute(RequestIdContext.MDC_KEY)).isEqualTo("req-20260718-001");
        MDC.put(RequestIdContext.MDC_KEY, "req-20260718-001");
        assertThat(ApiResult.ok().getRequestId()).isEqualTo("req-20260718-001");
    }

    @Test
    void shouldReplaceUnsafeIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdContext.HEADER, "bad request\nwith-log-injection");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestIdFilter().doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader(RequestIdContext.HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(UUID.fromString(requestId)).isNotNull();
    }
}
