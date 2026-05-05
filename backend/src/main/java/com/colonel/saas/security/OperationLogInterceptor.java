package com.colonel.saas.security;

import com.colonel.saas.entity.OperationLog;
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
import java.util.UUID;

@Slf4j
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final String ATTR_STARTED_AT = "operationLog.startedAt";

    private final OperationLogService operationLogService;

    public OperationLogInterceptor(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        request.setAttribute(ATTR_STARTED_AT, System.currentTimeMillis());
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex) {
        if (!shouldRecord(request.getMethod())) {
            return;
        }

        try {
            OperationLog entry = new OperationLog();
            Object userId = request.getAttribute("userId");
            if (userId instanceof UUID uuid) {
                entry.setUserId(uuid);
            }
            Object username = request.getAttribute("username");
            entry.setUsername(username == null ? null : String.valueOf(username));

            entry.setRequestMethod(request.getMethod());
            entry.setRequestUrl(request.getRequestURI());
            entry.setResponseCode(String.valueOf(response.getStatus()));
            entry.setIpAddress(resolveClientIp(request));
            entry.setUserAgent(request.getHeader("User-Agent"));
            entry.setDurationMs(resolveDuration(request));
            entry.setErrorMessage(ex == null ? null : ex.getMessage());
            entry.setRequestParams(copyParams(request.getParameterMap()));

            if (handler instanceof HandlerMethod handlerMethod) {
                populateHandlerMetadata(entry, handlerMethod, request);
            } else {
                entry.setModule(resolveModuleFromPath(request.getRequestURI()));
                entry.setAction(request.getMethod() + " " + request.getRequestURI());
                entry.setTargetType(resolveModuleFromPath(request.getRequestURI()));
                entry.setTargetName(request.getRequestURI());
                entry.setContent(request.getRequestURI());
            }

            operationLogService.record(entry);
        } catch (Exception ex2) {
            log.warn("operation log record failed: method={}, uri={}", request.getMethod(), request.getRequestURI(), ex2);
        }
    }

    private boolean shouldRecord(String method) {
        if (method == null) {
            return false;
        }
        return switch (method.toUpperCase()) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    private void populateHandlerMetadata(OperationLog log, HandlerMethod handlerMethod, HttpServletRequest request) {
        Tag tag = handlerMethod.getBeanType().getAnnotation(Tag.class);
        Operation operation = handlerMethod.getMethodAnnotation(Operation.class);
        log.setModule(tag == null ? resolveModuleFromPath(request.getRequestURI()) : tag.name());
        log.setAction(operation == null ? handlerMethod.getMethod().getName() : operation.summary());
        log.setContent(operation == null ? request.getRequestURI() : operation.description());
        log.setTargetType(handlerMethod.getBeanType().getSimpleName());
        log.setTargetName(operation == null ? request.getRequestURI() : operation.summary());

        Object pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables instanceof Map<?, ?> vars && !vars.isEmpty()) {
            Object lastValue = null;
            for (Object value : vars.values()) {
                lastValue = value;
            }
            if (lastValue != null) {
                log.setTargetId(String.valueOf(lastValue));
            }
        }
    }

    private Map<String, Object> copyParams(Map<String, String[]> parameterMap) {
        if (parameterMap == null || parameterMap.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        parameterMap.forEach((key, value) -> {
            if (value == null) {
                result.put(key, null);
            } else if (value.length == 1) {
                result.put(key, value[0]);
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private long resolveDuration(HttpServletRequest request) {
        Object startedAt = request.getAttribute(ATTR_STARTED_AT);
        if (startedAt instanceof Long started) {
            return Math.max(System.currentTimeMillis() - started, 0L);
        }
        return 0L;
    }

    private String resolveModuleFromPath(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return "系统";
        }
        String normalized = requestUri.startsWith("/") ? requestUri.substring(1) : requestUri;
        int slashIndex = normalized.indexOf('/');
        return slashIndex < 0 ? normalized : normalized.substring(0, slashIndex);
    }
}
