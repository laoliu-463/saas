package com.colonel.saas.aspect;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleGuardAspectTest {

    private final RoleGuardAspect aspect = new RoleGuardAspect(new CurrentUserPermissionPolicy());

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldProceedWhenNoRequireRolesAnnotation() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(OpenController.class, "ping");

        aspect.guard(point);

        verify(point, times(1)).proceed();
    }

    @Test
    void shouldProceedWhenCurrentRoleMatches() throws Throwable {
        bindRoleCodes(List.of(RoleCodes.BIZ_STAFF));
        ProceedingJoinPoint point = mockJoinPoint(ProtectedController.class, "bizEndpoint");

        aspect.guard(point);

        verify(point, times(1)).proceed();
    }

    @Test
    void shouldProceedWhenCurrentRoleIsAdmin() throws Throwable {
        bindRoleCodes("admin");
        ProceedingJoinPoint point = mockJoinPoint(ProtectedController.class, "bizEndpoint");

        aspect.guard(point);

        verify(point, times(1)).proceed();
    }

    @Test
    void shouldProceedWhenRoleCodesNeedUserPolicyNormalization() throws Throwable {
        bindRoleCodes("[ BIZ_STAFF ]");
        ProceedingJoinPoint point = mockJoinPoint(ProtectedController.class, "bizEndpoint");

        aspect.guard(point);

        verify(point, times(1)).proceed();
    }

    @Test
    void shouldThrowForbiddenWhenNoRoleMatched() throws Throwable {
        bindRoleCodes(List.of(RoleCodes.CHANNEL_STAFF));
        ProceedingJoinPoint point = mockJoinPoint(ProtectedController.class, "bizEndpoint");

        assertThatThrownBy(() -> aspect.guard(point))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldUseMethodAnnotationInsteadOfClassAnnotation() throws Throwable {
        bindRoleCodes(List.of(RoleCodes.BIZ_STAFF));
        ProceedingJoinPoint point = mockJoinPoint(MethodOverrideController.class, "adminEndpoint");

        assertThatThrownBy(() -> aspect.guard(point))
                .isInstanceOf(ForbiddenException.class);
    }

    private void bindRoleCodes(Object roleCodes) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("roleCodes", roleCodes);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ProceedingJoinPoint mockJoinPoint(Class<?> targetClass, String methodName) throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        Method method = targetClass.getMethod(methodName);

        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(point.getSignature()).thenReturn(signature);
        when(point.getTarget()).thenReturn(targetClass.getDeclaredConstructor().newInstance());
        when(point.proceed()).thenReturn(null);
        return point;
    }

    static class OpenController {
        public void ping() {
        }
    }

    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    static class ProtectedController {
        public void bizEndpoint() {
        }
    }

    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    static class MethodOverrideController {
        @RequireRoles({RoleCodes.ADMIN})
        public void adminEndpoint() {
        }
    }
}
