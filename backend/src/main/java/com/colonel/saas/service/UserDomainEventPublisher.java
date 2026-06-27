package com.colonel.saas.service;

import com.colonel.saas.domain.user.application.UserDomainEventPublisherApplicationService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 用户域领域事件发布器（DDD 委派壳）。
 *
 * <p>委派到 {@link UserDomainEventPublisherApplicationService}，保留旧签名以兼容遗留调用方。</p>
 *
 * @deprecated 请直接注入 {@link UserDomainEventPublisherApplicationService}
 */
@Service
@Deprecated
public class UserDomainEventPublisher {

    private final UserDomainEventPublisherApplicationService applicationService;

    public UserDomainEventPublisher(UserDomainEventPublisherApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public void publishUserCreated(
            UUID userId, String username, String realName,
            UUID roleId, String roleCode,
            UUID groupId, UUID deptId,
            Integer status, UUID operatorId) {
        applicationService.publishUserCreated(userId, username, realName, roleId, roleCode, groupId, deptId, status, operatorId);
    }

    public void publishUserDisabled(UUID userId, Integer oldStatus, Integer newStatus, UUID operatorId) {
        applicationService.publishUserDisabled(userId, oldStatus, newStatus, operatorId);
    }

    public void publishUserGroupChanged(
            UUID userId, UUID oldGroupId, UUID newGroupId,
            UUID oldDeptId, UUID newDeptId, UUID operatorId) {
        applicationService.publishUserGroupChanged(userId, oldGroupId, newGroupId, oldDeptId, newDeptId, operatorId);
    }

    public void publishRolePermissionUpdated(
            UUID roleId, String roleCode,
            String oldPermissionHash, String newPermissionHash, UUID operatorId) {
        applicationService.publishRolePermissionUpdated(roleId, roleCode, oldPermissionHash, newPermissionHash, operatorId);
    }
}