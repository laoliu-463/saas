package com.colonel.saas.domain.user.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.service.UserDomainService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 当前用户应用服务（DDD-USER-MIGRATION-006）。
 *
 * <p>收口 {@code /users/current} 的用户上下文、数据范围、权限检查和密码修改入口。
 * 现阶段委派旧 {@link UserDomainService}，保持 HTTP 行为不变。</p>
 */
@Service
public class CurrentUserApplicationService {

    private final UserDomainService userDomainService;

    public CurrentUserApplicationService(UserDomainService userDomainService) {
        this.userDomainService = userDomainService;
    }

    public CurrentUserResponse currentUser(
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            List<String> roleCodes) {
        return userDomainService.getCurrentUser(userId, deptId, dataScope, safeRoleCodes(roleCodes));
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        userDomainService.changePassword(userId, request);
    }

    public UserDataScopeResponse dataScope(UUID userId, UUID deptId, DataScope dataScope) {
        return userDomainService.getUserDataScope(userId, deptId, dataScope);
    }

    public CheckPermissionResponse checkPermission(
            UUID userId,
            List<String> roleCodes,
            CheckPermissionRequest request) {
        return userDomainService.checkPermission(userId, safeRoleCodes(roleCodes), request);
    }

    private List<String> safeRoleCodes(List<String> roleCodes) {
        return roleCodes == null ? Collections.emptyList() : roleCodes;
    }
}
