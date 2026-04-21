package com.colonel.saas.aspect;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.colonel.saas.annotation.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.UUID;

/**
 * 在 Mapper 层自动追加数据范围条件。
 */
@Aspect
@Component
public class DataScopeAspect {

    @Around("@annotation(dataScope)")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object around(ProceedingJoinPoint point, DataScope dataScope) throws Throwable {
        AbstractWrapper<?, ?, ?> wrapper = findWrapper(point.getArgs());
        if (wrapper == null) {
            return point.proceed();
        }

        ScopeContext context = resolveContext();
        if (context == null || context.scope() == null) {
            return point.proceed();
        }
        AbstractWrapper rawWrapper = (AbstractWrapper) wrapper;

        switch (context.scope()) {
            case PERSONAL -> {
                if (context.userId() == null) {
                    throw new BusinessException("数据权限异常：缺少用户上下文");
                }
                rawWrapper.eq(dataScope.userField(), context.userId());
            }
            case DEPT -> {
                if (context.deptId() == null) {
                    throw new BusinessException("数据权限异常：缺少部门上下文");
                }
                rawWrapper.eq("dept_id", context.deptId());
            }
            case ALL -> {
                // 全量数据，无附加过滤条件
            }
        }
        return point.proceed();
    }

    private AbstractWrapper<?, ?, ?> findWrapper(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof AbstractWrapper<?, ?, ?> wrapper) {
                return wrapper;
            }
        }
        return null;
    }

    private ScopeContext resolveContext() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        HttpServletRequest request = servletAttrs.getRequest();

        UUID userId = asUuid(request.getAttribute("userId"));
        UUID deptId = asUuid(request.getAttribute("deptId"));
        com.colonel.saas.common.enums.DataScope scope = asScope(request.getAttribute("dataScope"));
        return new ScopeContext(userId, deptId, scope);
    }

    private UUID asUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        String text = raw.toString();
        if (text.isBlank()) {
            return null;
        }
        return UUID.fromString(text);
    }

    private com.colonel.saas.common.enums.DataScope asScope(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof com.colonel.saas.common.enums.DataScope scope) {
            return scope;
        }
        if (raw instanceof Number number) {
            return com.colonel.saas.common.enums.DataScope.fromCode(number.intValue());
        }
        String text = Objects.toString(raw, "").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return com.colonel.saas.common.enums.DataScope.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return com.colonel.saas.common.enums.DataScope.fromCode(Integer.parseInt(text));
        }
    }

    private record ScopeContext(UUID userId, UUID deptId, com.colonel.saas.common.enums.DataScope scope) {
    }
}
