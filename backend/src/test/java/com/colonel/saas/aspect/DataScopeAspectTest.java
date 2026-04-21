package com.colonel.saas.aspect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.annotation.DataScope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DataScopeAspectTest {

    private final DataScopeAspect aspect = new DataScopeAspect();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldAppendPersonalCondition() throws Throwable {
        UUID userId = UUID.randomUUID();
        bindRequest(userId, com.colonel.saas.common.enums.DataScope.PERSONAL);

        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        ProceedingJoinPoint joinPoint = mockJoinPoint(wrapper);
        DataScope annotation = annotation();

        aspect.around(joinPoint, annotation);

        String sqlSegment = wrapper.getSqlSegment();
        assertThat(sqlSegment).contains("user_id");
        assertThat(sqlSegment).contains("MPGENVAL");
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void shouldNotAppendConditionForAllScope() throws Throwable {
        bindRequest(UUID.randomUUID(), com.colonel.saas.common.enums.DataScope.ALL);

        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        ProceedingJoinPoint joinPoint = mockJoinPoint(wrapper);
        DataScope annotation = annotation();

        aspect.around(joinPoint, annotation);

        assertThat(wrapper.getSqlSegment()).isEmpty();
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void shouldAppendDeptCondition() throws Throwable {
        UUID deptId = UUID.randomUUID();
        bindRequest(UUID.randomUUID(), deptId, com.colonel.saas.common.enums.DataScope.DEPT);

        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        ProceedingJoinPoint joinPoint = mockJoinPoint(wrapper);
        DataScope annotation = annotation();

        aspect.around(joinPoint, annotation);

        String sqlSegment = wrapper.getSqlSegment();
        assertThat(sqlSegment).contains("dept_id");
        assertThat(sqlSegment).contains("MPGENVAL");
        verify(joinPoint, times(1)).proceed();
    }

    private void bindRequest(UUID userId, com.colonel.saas.common.enums.DataScope scope) {
        bindRequest(userId, UUID.randomUUID(), scope);
    }

    private void bindRequest(UUID userId, UUID deptId, com.colonel.saas.common.enums.DataScope scope) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        request.setAttribute("deptId", deptId);
        request.setAttribute("dataScope", scope);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ProceedingJoinPoint mockJoinPoint(QueryWrapper<Object> wrapper) throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getArgs()).thenReturn(new Object[]{wrapper});
        when(point.proceed()).thenReturn(null);
        return point;
    }

    private DataScope annotation() throws NoSuchMethodException {
        Method method = DummyMapper.class.getMethod("query", QueryWrapper.class);
        return method.getAnnotation(DataScope.class);
    }

    interface DummyMapper {
        @DataScope(userField = "user_id")
        void query(QueryWrapper<Object> wrapper);
    }
}
