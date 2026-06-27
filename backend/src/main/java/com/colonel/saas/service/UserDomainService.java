package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 用户域核心服务（DDD 委派壳）。
 *
 * <p>委派到 {@link com.colonel.saas.domain.user.application.CurrentUserApplicationService}，
 * 保留旧签名以兼容 Controller 调用方。生产路径业务规则由 DDD Application 实现。</p>
 *
 * <p><b>业务域：</b>用户域 — 核心服务</p>
 */
@Service
public class UserDomainService {

    private final com.colonel.saas.domain.user.application.CurrentUserApplicationService applicationService;

    public UserDomainService(
            com.colonel.saas.domain.user.application.CurrentUserApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public CurrentUserResponse getCurrentUser(
            UUID userId,
            UUID deptId,
            DataScope requestScope,
            List<String> requestRoleCodes) {
        return applicationService.getCurrentUser(userId, deptId, requestScope, requestRoleCodes);
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        applicationService.changePassword(userId, request);
    }

    public UserDataScopeResponse getUserDataScope(UUID userId, UUID deptId, DataScope dataScope) {
        return applicationService.getUserDataScope(userId, deptId, dataScope);
    }

    public CheckPermissionResponse checkPermission(
            UUID userId,
            List<String> requestRoleCodes,
            CheckPermissionRequest request) {
        return applicationService.checkPermission(userId, requestRoleCodes, request);
    }
}