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

/**
 * API 耗时统计过滤器。
 *
 * <p>作为 Spring Servlet 过滤器，对每个 API 请求记录处理耗时和状态码，
 * 用于性能监控和慢接口排查。慢接口（超过 1 秒）将以 WARN 级别记录，
 * 便于日志监控系统（如 ELK）自动告警。</p>
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>在请求进入时记录起始时间（纳秒精度）</li>
 *   <li>将请求传递给后续过滤器链和控制器处理</li>
 *   <li>无论处理成功还是异常，都在 finally 块中计算耗时并记录日志</li>
 *   <li>异常场景下，状态码取 HTTP 响应状态码和 500 中的较大值</li>
 * </ol>
 *
 * <h3>日志格式</h3>
 * <pre>
 * [api timing] method=GET uri=/api/talent/list status=200 durationMs=123 error=
 * [api timing] method=POST uri=/api/order/create status=500 durationMs=1523 slow=true error=NullPointerException
 * </pre>
 *
 * <h3>过滤范围</h3>
 * <p>仅过滤以 {@code /api/} 开头的请求路径，静态资源和健康检查等
 * 非业务接口不会产生日志噪音。</p>
 *
 * <h3>性能影响</h3>
 * <p>此过滤器使用 {@code System.nanoTime()} 计时，开销极小（纳秒级），
 * 不会对业务请求产生可感知的性能影响。</p>
 */
@Slf4j
@Component
public class ApiTimingFilter extends OncePerRequestFilter {

    /** 慢接口阈值（毫秒），超过此值的请求将以 WARN 级别记录 */
    private static final long SLOW_API_THRESHOLD_MS = 1000L;

    /**
     * 判断请求是否应跳过过滤器。
     *
     * <p>仅对以 {@code /api/} 开头的业务接口进行耗时统计，
     * 静态资源、健康检查等路径不统计。</p>
     *
     * @param request HTTP 请求
     * @return true 表示跳过过滤，false 表示执行过滤
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (contextPath == null || contextPath.isBlank()) {
            // 无 contextPath 时，仅对 /api/ 路径进行统计
            return requestUri == null || !requestUri.startsWith("/api/");
        }
        // 有 contextPath 时，对 contextPath 下的所有路径进行统计
        return requestUri == null || !(requestUri.equals(contextPath) || requestUri.startsWith(contextPath + "/"));
    }

    /**
     * 执行请求耗时统计。
     *
     * <p>包裹整个过滤器链的执行，使用 try-finally 确保无论是否发生异常
     * 都能记录耗时。异常被重新抛出，不影响全局异常处理器的正常工作。</p>
     *
     * @param request       HTTP 请求
     * @param response      HTTP 响应
     * @param filterChain   过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        // 使用纳秒精度计时，避免 System.currentTimeMillis() 的精度问题
        long startedAt = System.nanoTime();
        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            // 记录异常信息，但不吞掉异常，继续向上抛出
            failure = ex;
            throw ex;
        } finally {
            // 计算耗时（毫秒）
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            // 异常场景下确保状态码不低于 500
            int status = failure == null ? response.getStatus() : Math.max(response.getStatus(), 500);
            String method = request.getMethod();
            String uri = request.getRequestURI();

            // 慢接口使用 WARN 级别记录，便于日志监控系统自动告警
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
