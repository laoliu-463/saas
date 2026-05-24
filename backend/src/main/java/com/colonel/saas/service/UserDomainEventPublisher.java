package com.colonel.saas.service;

import com.colonel.saas.domain.user.event.RolePermissionUpdatedEvent;
import com.colonel.saas.domain.user.event.UserCreatedEvent;
import com.colonel.saas.domain.user.event.UserDisabledEvent;
import com.colonel.saas.domain.user.event.UserDomainEventHeaders;
import com.colonel.saas.domain.user.event.UserGroupChangedEvent;
import com.colonel.saas.domain.user.event.UserStatusLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户域领域事件发布：Spring ApplicationEvent + 事务提交后由监听器消费。
 */
@Service
public class UserDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserDomainEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public UserDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishUserCreated(
            UUID userId,
            String username,
            String realName,
            UUID roleId,
            String roleCode,
            UUID groupId,
            UUID deptId,
            Integer status,
            UUID operatorId) {
        UUID groupOrDept = groupId != null ? groupId : deptId;
        publish(new UserCreatedEvent(
                UserDomainEventHeaders.newEventId(),
                userId,
                username,
                realName,
                roleId,
                roleCode,
                groupOrDept,
                deptId,
                UserStatusLabel.fromCode(status),
                operatorId,
                LocalDateTime.now(),
                UserDomainEventHeaders.currentTraceId()));
    }

    public void publishUserDisabled(
            UUID userId,
            Integer oldStatus,
            Integer newStatus,
            UUID operatorId) {
        publish(new UserDisabledEvent(
                UserDomainEventHeaders.newEventId(),
                userId,
                UserStatusLabel.fromCode(oldStatus),
                UserStatusLabel.fromCode(newStatus),
                operatorId,
                LocalDateTime.now(),
                UserDomainEventHeaders.currentTraceId()));
    }

    public void publishUserGroupChanged(
            UUID userId,
            UUID oldGroupId,
            UUID newGroupId,
            UUID oldDeptId,
            UUID newDeptId,
            UUID operatorId) {
        publish(new UserGroupChangedEvent(
                UserDomainEventHeaders.newEventId(),
                userId,
                oldGroupId,
                newGroupId,
                oldDeptId,
                newDeptId,
                operatorId,
                LocalDateTime.now(),
                UserDomainEventHeaders.currentTraceId()));
    }

    public void publishRolePermissionUpdated(
            UUID roleId,
            String roleCode,
            String oldPermissionHash,
            String newPermissionHash,
            UUID operatorId) {
        publish(new RolePermissionUpdatedEvent(
                UserDomainEventHeaders.newEventId(),
                roleId,
                roleCode,
                oldPermissionHash,
                newPermissionHash,
                operatorId,
                LocalDateTime.now(),
                UserDomainEventHeaders.currentTraceId()));
    }

    private void publish(Object event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("用户域事件发布失败，已忽略: eventType={}", event.getClass().getSimpleName(), ex);
        }
    }
}
