package com.colonel.saas.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiTimingFilterTest {

    private final TestableApiTimingFilter filter = new TestableApiTimingFilter();

    @Test
    void shouldFilterOnlyApiPathsWhenNoContextPath() {
        assertThat(filter.shouldFilter(request("/api/orders"))).isTrue();
        assertThat(filter.shouldFilter(request("/orders"))).isFalse();
        assertThat(filter.shouldFilter(request(null))).isFalse();
    }

    @Test
    void shouldFilterApplicationPathsWhenContextPathIsPresent() {
        MockHttpServletRequest exactContext = request("/saas");
        exactContext.setContextPath("/saas");
        MockHttpServletRequest nestedPath = request("/saas/api/orders");
        nestedPath.setContextPath("/saas");
        MockHttpServletRequest otherPath = request("/other/api/orders");
        otherPath.setContextPath("/saas");

        assertThat(filter.shouldFilter(exactContext)).isTrue();
        assertThat(filter.shouldFilter(nestedPath)).isTrue();
        assertThat(filter.shouldFilter(otherPath)).isFalse();
    }

    @Test
    void doFilterInternalShouldInvokeChainAndPreserveStatus() throws Exception {
        MockHttpServletRequest request = request("/api/orders");
        request.setMethod("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();
        FilterChain chain = (req, res) -> {
            calls.incrementAndGet();
            ((MockHttpServletResponse) res).setStatus(204);
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(calls).hasValue(1);
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    void doFilterInternalShouldRethrowFailuresAfterTiming() {
        MockHttpServletRequest request = request("/api/orders");
        request.setMethod("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, chain))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("boom");
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    private static class TestableApiTimingFilter extends ApiTimingFilter {
        boolean shouldFilter(MockHttpServletRequest request) {
            return !shouldNotFilter(request);
        }

        @Override
        public void doFilterInternal(
                jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            super.doFilterInternal(request, response, filterChain);
        }
    }
}
