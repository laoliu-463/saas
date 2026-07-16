package com.colonel.saas.security;

import com.colonel.saas.entity.OperationLog;
import com.colonel.saas.domain.talent.policy.TalentComplaintPolicy;
import com.colonel.saas.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 操作日志拦截器，负责记录所有写操作（POST、PUT、PATCH、DELETE）的审计日志。
 *
 * <p>架构角色：
 * <ul>
 *   <li>实现 {@link HandlerInterceptor} 接口，在请求处理完成后（{@code afterCompletion}）
 *       异步记录操作日志，不影响请求响应延迟。</li>
 *   <li>作为安全层的审计组件，记录谁在什么时间对什么资源执行了什么操作，
 *       为合规审计和问题追溯提供数据支撑。</li>
 *   <li>通过 {@link OperationLogService} 持久化日志记录。</li>
 * </ul>
 *
 * <p>记录的信息包括：
 * <ul>
 *   <li>操作人（userId、username，从 request attribute 中获取，由 {@link JwtAuthenticationFilter} 写入）</li>
 *   <li>请求信息（HTTP 方法、URL、请求参数）</li>
 *   <li>响应信息（HTTP 状态码、耗时毫秒数）</li>
 *   <li>客户端信息（IP 地址、User-Agent）</li>
 *   <li>控制器元数据（模块名、操作描述、目标类型，从 Swagger 注解中提取）</li>
 *   <li>异常信息（若有）</li>
 * </ul>
 *
 * <p>与其他组件的关系：
 * <ul>
 *   <li>{@link JwtAuthenticationFilter} — 负责身份认证，
 *       认证后的 userId/username 写入 request attribute，本拦截器从中读取</li>
 *   <li>{@link OperationLogService} — 日志持久化服务</li>
 *   <li>{@link OperationLog} — 操作日志实体</li>
 * </ul>
 *
 * <p>注意：仅记录写操作（POST/PUT/PATCH/DELETE），GET 请求不记录审计日志。
 * 日志记录失败不影响正常业务流程，仅打印警告日志。
 */
@Slf4j
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    /** request attribute 键名，用于在 preHandle 和 afterCompletion 之间传递请求开始时间 */
    private static final String ATTR_STARTED_AT = "operationLog.startedAt";
    private static final int MAX_COMPLAINT_AUDIT_VALUE_CODE_POINTS = 256;
    private static final int MAX_COMPLAINT_AUDIT_VALUES = 16;
    private static final Set<String> COMPLAINT_REASONS = Set.of(
            TalentComplaintPolicy.REPEATED_NO_FULFILLMENT,
            TalentComplaintPolicy.LOW_PRICE_RESALE,
            TalentComplaintPolicy.OTHER);

    /** 操作日志持久化服务 */
    private final OperationLogService operationLogService;

    /**
     * 构造函数，通过 Spring 依赖注入创建拦截器实例。
     *
     * @param operationLogService 操作日志持久化服务
     */
    public OperationLogInterceptor(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 请求处理前：记录请求开始时间戳。
     *
     * <p>将当前时间写入 request attribute，供 {@link #afterCompletion} 计算请求耗时。
     * 始终返回 {@code true}（不阻断请求处理）。
     *
     * @param request  当前 HTTP 请求
     * @param response HTTP 响应
     * @param handler  请求对应的处理器
     * @return 始终返回 {@code true}，不阻断请求
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        // 记录请求开始时间，用于后续计算请求耗时
        request.setAttribute(ATTR_STARTED_AT, System.currentTimeMillis());
        return true;
    }

    /**
     * 请求处理完成后（包括视图渲染）：组装并持久化操作日志。
     *
     * <p>处理流程：
     * <ol>
     *   <li>通过 {@link #shouldRecord} 判断是否需要记录（仅 POST/PUT/PATCH/DELETE）</li>
     *   <li>从 request attribute 读取操作人信息（由 {@link JwtAuthenticationFilter} 写入）</li>
     *   <li>组装基础日志字段（HTTP 方法、URL、状态码、IP、User-Agent、耗时、异常信息、请求参数）</li>
     *   <li>若处理器为 {@link HandlerMethod}，从 Swagger 注解提取模块名、操作描述等元数据；
     *       否则从 URL 路径推断模块名</li>
     *   <li>通过 {@link OperationLogService#record} 持久化日志</li>
     * </ol>
     *
     * <p>日志记录失败不会影响正常业务流程，仅打印警告日志。
     *
     * @param request  当前 HTTP 请求
     * @param response HTTP 响应
     * @param handler  请求对应的处理器（可能是 HandlerMethod 或其他类型，如静态资源处理器）
     * @param ex       请求处理过程中抛出的异常，若无异常则为 {@code null}
     */
    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex) {
        // 仅记录写操作，GET 请求不记录审计日志
        if (!shouldRecord(request.getMethod())) {
            return;
        }

        try {
            OperationLog entry = new OperationLog();

            // 从 request attribute 读取操作人信息（由认证过滤器/拦截器写入）
            Object userId = request.getAttribute("userId");
            if (userId instanceof UUID uuid) {
                entry.setUserId(uuid);
            }
            Object username = request.getAttribute("username");
            entry.setUsername(username == null ? null : String.valueOf(username));

            // 组装请求和响应基础信息
            entry.setRequestMethod(request.getMethod());
            entry.setRequestUrl(request.getRequestURI());
            entry.setResponseCode(String.valueOf(response.getStatus()));
            entry.setIpAddress(resolveClientIp(request));
            entry.setUserAgent(request.getHeader("User-Agent"));
            entry.setDurationMs(resolveDuration(request));
            entry.setErrorMessage(ex == null ? null : ex.getMessage());
            entry.setRequestParams(copyParams(request.getParameterMap(), request.getRequestURI()));

            // 根据处理器类型提取模块和操作元数据
            if (handler instanceof HandlerMethod handlerMethod) {
                // 有对应的控制器方法：从 Swagger 注解提取模块名和操作描述
                populateHandlerMetadata(entry, handlerMethod, request);
            } else {
                // 无对应控制器方法（如静态资源、错误页面）：从 URL 路径推断
                entry.setModule(resolveModuleFromPath(request.getRequestURI()));
                entry.setAction(request.getMethod() + " " + request.getRequestURI());
                entry.setTargetType(resolveModuleFromPath(request.getRequestURI()));
                entry.setTargetName(request.getRequestURI());
                entry.setContent(request.getRequestURI());
            }

            // 持久化操作日志
            operationLogService.record(entry);
        } catch (Exception ex2) {
            // 日志记录失败不影响业务流程，仅打印警告
            log.warn("operation log record failed: method={}, uri={}", request.getMethod(), request.getRequestURI(), ex2);
        }
    }

    /**
     * 判断指定 HTTP 方法是否需要记录操作日志。
     * <p>仅记录写操作（POST、PUT、PATCH、DELETE），GET/HEAD/OPTIONS 等读操作不记录。
     *
     * @param method HTTP 方法名
     * @return {@code true} 需要记录，{@code false} 不需要记录
     */
    private boolean shouldRecord(String method) {
        if (method == null) {
            return false;
        }
        return switch (method.toUpperCase()) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    /**
     * 从控制器方法的 Swagger 注解中提取模块名和操作描述等元数据。
     *
     * <p>优先使用 {@code @Tag} 注解的 name 作为模块名，
     * 使用 {@code @Operation} 注解的 summary 和 description 作为操作描述。
     * 若注解缺失则回退到方法名和 URL 路径。
     *
     * <p>同时尝试从路径变量中提取目标资源 ID（取最后一个路径变量的值）。
     *
     * @param entry          操作日志实体，方法执行后其元数据字段将被填充
     * @param handlerMethod  处理器方法对象，用于读取注解信息
     * @param request        当前 HTTP 请求，用于提取路径变量
     */
    private void populateHandlerMetadata(OperationLog entry, HandlerMethod handlerMethod, HttpServletRequest request) {
        // 从控制器类上读取 @Tag 注解（Swagger 模块分组）
        Tag tag = handlerMethod.getBeanType().getAnnotation(Tag.class);
        // 从控制器方法上读取 @Operation 注解（API 操作描述）
        Operation operation = handlerMethod.getMethodAnnotation(Operation.class);

        entry.setModule(tag == null ? resolveModuleFromPath(request.getRequestURI()) : tag.name());
        entry.setAction(operation == null ? handlerMethod.getMethod().getName() : operation.summary());
        entry.setContent(operation == null ? request.getRequestURI() : operation.description());
        entry.setTargetType(handlerMethod.getBeanType().getSimpleName());
        entry.setTargetName(operation == null ? request.getRequestURI() : operation.summary());

        // 尝试从路径变量中提取目标资源 ID
        // 例如 /users/{id} 中的 id 值；取最后一个路径变量值作为 targetId
        Object pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables instanceof Map<?, ?> vars && !vars.isEmpty()) {
            Object lastValue = null;
            for (Object value : vars.values()) {
                lastValue = value;
            }
            if (lastValue != null) {
                entry.setTargetId(String.valueOf(lastValue));
            }
        }
    }

    /**
     * 将 Servlet 请求参数映射复制为可序列化的 Map。
     *
     * <p>Servlet 的参数值类型为 {@code String[]}（多值参数），本方法将其简化为：
     * <ul>
     *   <li>单值参数 → 直接存为 {@code String}</li>
     *   <li>多值参数 → 保持 {@code String[]} 不变</li>
     *   <li>null 值 → 存为 {@code null}</li>
     * </ul>
     *
     * @param parameterMap Servlet 原始参数映射
     * @return 复制后的参数 Map，可安全序列化；若输入为空则返回 {@code null}
     */
    private Map<String, Object> copyParams(
            Map<String, String[]> parameterMap,
            String requestUri) {
        if (parameterMap == null || parameterMap.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        boolean complaintCreate = isComplaintCreateRoute(requestUri);
        parameterMap.forEach((key, value) -> {
            if (complaintCreate && "content".equals(key)) {
                result.put(key, "[REDACTED]");
                return;
            }
            if (complaintCreate && "reason".equals(key)) {
                result.put(key, value != null
                                && value.length == 1
                                && COMPLAINT_REASONS.contains(value[0])
                        ? value[0]
                        : "[INVALID]");
                return;
            }
            if (complaintCreate) {
                result.put(key, sanitizeComplaintAuditValues(value));
                return;
            }
            if (value == null) {
                result.put(key, null);
            } else if (value.length == 1) {
                result.put(key, value[0]);           // 单值参数简化为 String
            } else {
                result.put(key, value);              // 多值参数保持 String[]
            }
        });
        return result;
    }

    private Object sanitizeComplaintAuditValues(String[] values) {
        if (values == null) {
            return null;
        }
        if (values.length > MAX_COMPLAINT_AUDIT_VALUES) {
            return "[TRUNCATED]";
        }
        String[] sanitized = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            String value = values[index];
            sanitized[index] = value != null
                    && value.codePointCount(0, value.length())
                            <= MAX_COMPLAINT_AUDIT_VALUE_CODE_POINTS
                    ? value
                    : "[TRUNCATED]";
        }
        return sanitized.length == 1 ? sanitized[0] : sanitized;
    }

    private boolean isComplaintCreateRoute(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        return requestUri.matches(
                "^/(?:api/)?samples/[0-9a-fA-F-]{36}/complaints/?$");
    }

    /**
     * 解析客户端真实 IP 地址。
     *
     * <p>优先从 X-Forwarded-For 头部获取（经过反向代理时），
     * 取第一个 IP（即最靠近客户端的 IP）。
     * 若无代理头则回退到 {@code getRemoteAddr()}。
     *
     * @param request 当前 HTTP 请求
     * @return 客户端 IP 地址字符串
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            // X-Forwarded-For 格式：client, proxy1, proxy2；取第一个即为客户端真实 IP
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        // 无代理头时直接获取远端地址
        return request.getRemoteAddr();
    }

    /**
     * 计算请求处理耗时（毫秒）。
     *
     * <p>从 request attribute 中读取 preHandle 阶段记录的开始时间戳，
     * 与当前时间做差。若开始时间不存在则返回 0。
     *
     * @param request 当前 HTTP 请求
     * @return 请求处理耗时（毫秒），最小值为 0
     */
    private long resolveDuration(HttpServletRequest request) {
        Object startedAt = request.getAttribute(ATTR_STARTED_AT);
        if (startedAt instanceof Long started) {
            return Math.max(System.currentTimeMillis() - started, 0L);  // 保底返回 0，防止时钟回拨导致负数
        }
        return 0L;
    }

    /**
     * 从请求 URI 中推断模块名称。
     *
     * <p>取 URI 的第一级路径作为模块名。例如：
     * <ul>
     *   <li>{@code /orders/123} → "orders"</li>
     *   <li>{@code /users/current} → "users"</li>
     *   <li>{@code /} → "系统"（默认值）</li>
     * </ul>
     *
     * @param requestUri 请求 URI
     * @return 模块名称字符串
     */
    private String resolveModuleFromPath(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return "系统";  // URI 为空时使用默认模块名
        }
        // 去掉开头的斜杠
        String normalized = requestUri.startsWith("/") ? requestUri.substring(1) : requestUri;
        int slashIndex = normalized.indexOf('/');
        // 无后续斜杠说明只有模块名（如 /orders），否则截取第一段
        return slashIndex < 0 ? normalized : normalized.substring(0, slashIndex);
    }
}
