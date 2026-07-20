package com.colonel.saas.aspect;

import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.CurrentUserProvider;
import com.colonel.saas.domain.user.facade.AuthorizationFacade;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/** Enforces database-backed RBAC permissions at controller boundaries. */
@Aspect
@Component
@Order(10)
public class PermissionGuardAspect {

    private final AuthorizationFacade authorizationFacade;
    private final CurrentUserProvider currentUserProvider;

    public PermissionGuardAspect(
            AuthorizationFacade authorizationFacade,
            CurrentUserProvider currentUserProvider) {
        this.authorizationFacade = authorizationFacade;
        this.currentUserProvider = currentUserProvider;
    }

    @Around("execution(* com.colonel.saas.controller..*(..))")
    public Object guard(ProceedingJoinPoint point) throws Throwable {
        RequirePermission required = resolve(point);
        if (required == null) {
            return point.proceed();
        }
        AuthorizationDecision decision = authorizationFacade.authorize(
                currentUserProvider.currentUserId(), required.value());
        if (!decision.allowed()) {
            throw new ForbiddenException("无权限访问该接口");
        }
        return point.proceed();
    }

    private RequirePermission resolve(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        RequirePermission methodPermission = method.getAnnotation(RequirePermission.class);
        if (methodPermission != null) {
            return methodPermission;
        }
        return point.getTarget().getClass().getAnnotation(RequirePermission.class);
    }
}
