package com.colonel.saas.aspect;

import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationReason;
import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.api.CurrentUserProvider;
import com.colonel.saas.domain.user.facade.AuthorizationFacade;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionGuardAspectTest {

    private AuthorizationFacade authorizationFacade;
    private CurrentUserProvider currentUserProvider;
    private PermissionGuardAspect aspect;
    private UUID userId;

    @BeforeEach
    void setUp() {
        authorizationFacade = mock(AuthorizationFacade.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        aspect = new PermissionGuardAspect(authorizationFacade, currentUserProvider);
        userId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
    }

    @Test
    void shouldProceedWhenPermissionAnnotationIsAbsent() throws Throwable {
        ProceedingJoinPoint point = point(OpenController.class, "ping");

        aspect.guard(point);

        verify(point, times(1)).proceed();
    }

    @Test
    void shouldProceedWhenPermissionIsGranted() throws Throwable {
        ProceedingJoinPoint point = point(ProtectedController.class, "read");
        when(authorizationFacade.authorize(userId, "sample:access"))
                .thenReturn(decision(true, "sample:access"));

        aspect.guard(point);

        verify(point, times(1)).proceed();
    }

    @Test
    void shouldDenyWhenPermissionIsNotGranted() throws Throwable {
        ProceedingJoinPoint point = point(ProtectedController.class, "read");
        when(authorizationFacade.authorize(userId, "sample:access"))
                .thenReturn(decision(false, "sample:access"));

        assertThatThrownBy(() -> aspect.guard(point)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldPreferMethodPermissionOverClassPermission() throws Throwable {
        ProceedingJoinPoint point = point(ProtectedController.class, "write");
        when(authorizationFacade.authorize(userId, "sample:write"))
                .thenReturn(decision(true, "sample:write"));

        aspect.guard(point);

        verify(authorizationFacade).authorize(userId, "sample:write");
    }

    private AuthorizationDecision decision(boolean allowed, String permissionCode) {
        return new AuthorizationDecision(
                allowed,
                permissionCode,
                "sample",
                allowed ? AuthorizationScope.ALL : AuthorizationScope.DENY,
                allowed ? AuthorizationReason.GRANTED : AuthorizationReason.PERMISSION_NOT_GRANTED);
    }

    private ProceedingJoinPoint point(Class<?> targetClass, String methodName) throws Exception {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        Method method = targetClass.getMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(point.getSignature()).thenReturn(signature);
        when(point.getTarget()).thenReturn(targetClass.getDeclaredConstructor().newInstance());
        return point;
    }

    static class OpenController {
        public void ping() { }
    }

    @RequirePermission("sample:access")
    static class ProtectedController {
        public void read() { }

        @RequirePermission("sample:write")
        public void write() { }
    }
}
