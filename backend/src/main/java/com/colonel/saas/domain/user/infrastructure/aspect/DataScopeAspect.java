package com.colonel.saas.domain.user.infrastructure.aspect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.domain.user.api.CurrentUserProvider;
import com.colonel.saas.domain.user.api.DataScope;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Applies user-domain data scope to Mapper query wrappers.
 */
@Aspect
@Component
public class DataScopeAspect {

    private final UserDomainFacade userDomainFacade;
    private final CurrentUserProvider currentUserProvider;

    public DataScopeAspect(UserDomainFacade userDomainFacade, CurrentUserProvider currentUserProvider) {
        this.userDomainFacade = userDomainFacade;
        this.currentUserProvider = currentUserProvider;
    }

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint point, DataScope dataScope) throws Throwable {
        QueryWrapper<?> wrapper = findWrapper(point.getArgs());
        if (wrapper == null) {
            return point.proceed();
        }

        UUID currentUserId = currentUserProvider.currentUserId();
        if (currentUserId == null) {
            return point.proceed();
        }

        UserDataScopeResponse scope = userDomainFacade.resolveDataScope(currentUserId);
        if (scope == null) {
            return point.proceed();
        }

        List<UUID> userIds = scope.userIds();
        if (userIds == null || userIds.isEmpty()) {
            return point.proceed();
        }

        wrapper.in(dataScope.userField(), userIds);
        return point.proceed();
    }

    private QueryWrapper<?> findWrapper(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof QueryWrapper<?> wrapper) {
                return wrapper;
            }
        }
        return null;
    }
}
