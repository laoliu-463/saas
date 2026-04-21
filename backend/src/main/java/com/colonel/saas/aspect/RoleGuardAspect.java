package com.colonel.saas.aspect;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.RoleCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Aspect
@Component
public class RoleGuardAspect {

    @Around("execution(* com.colonel.saas.controller..*(..))")
    public Object guard(ProceedingJoinPoint point) throws Throwable {
        RequireRoles requireRoles = resolveRequireRoles(point);
        if (requireRoles == null || requireRoles.value().length == 0) {
            return point.proceed();
        }

        Set<String> currentRoles = resolveCurrentRoles();
        if (currentRoles.contains(RoleCodes.ADMIN)) {
            return point.proceed();
        }

        for (String role : requireRoles.value()) {
            if (currentRoles.contains(normalize(role))) {
                return point.proceed();
            }
        }

        throw new ForbiddenException("无权限访问该接口");
    }

    private RequireRoles resolveRequireRoles(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        RequireRoles methodAnnotation = method.getAnnotation(RequireRoles.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        Class<?> targetClass = point.getTarget().getClass();
        return targetClass.getAnnotation(RequireRoles.class);
    }

    private Set<String> resolveCurrentRoles() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttrs)) {
            return Set.of();
        }
        HttpServletRequest request = servletAttrs.getRequest();
        Object raw = request.getAttribute("roleCodes");
        if (raw == null) {
            return Set.of();
        }

        Set<String> roles = new HashSet<>();
        if (raw instanceof Collection<?> collection) {
            for (Object value : collection) {
                String normalized = normalize(Objects.toString(value, ""));
                if (!normalized.isBlank()) {
                    roles.add(normalized);
                }
            }
            return roles;
        }
        String text = Objects.toString(raw, "");
        if (text.isBlank()) {
            return Set.of();
        }
        Arrays.stream(text.split(","))
                .map(this::normalize)
                .filter(item -> !item.isBlank())
                .forEach(roles::add);
        return roles;
    }

    private String normalize(String roleCode) {
        return roleCode == null ? "" : roleCode.trim().toLowerCase(Locale.ROOT);
    }
}

