package com.colonel.saas.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ApiTimingFilter extends OncePerRequestFilter {

    private static final long SLOW_API_THRESHOLD_MS = 1000L;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (contextPath == null || contextPath.isBlank()) {
            return requestUri == null || !requestUri.startsWith("/api/");
        }
        return requestUri == null || !(requestUri.equals(contextPath) || requestUri.startsWith(contextPath + "/"));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            int status = failure == null ? response.getStatus() : Math.max(response.getStatus(), 500);
            String method = request.getMethod();
            String uri = request.getRequestURI();
            if (durationMs >= SLOW_API_THRESHOLD_MS) {
                log.warn("[api timing] method={} uri={} status={} durationMs={} slow=true error={}",
                        method, uri, status, durationMs, failure == null ? "" : failure.getClass().getSimpleName());
            } else {
                log.info("[api timing] method={} uri={} status={} durationMs={} error={}",
                        method, uri, status, durationMs, failure == null ? "" : failure.getClass().getSimpleName());
            }
        }
    }
}
