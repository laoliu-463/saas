package com.colonel.saas.domain.user.application;

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
 * 用户域领域事件发布应用服务（DDD-USER-EVENT-PUBLISHER）。
 *
 * <p>通过 Spring {@link ApplicationEventPublisher} 发布用户域领域事件，事件在当前事务
 * 提交后由监听器异步消费。发布失败时仅记录警告日志，不影响主业务流程。</p>
 *
 * <ul>
 *   <li>用户创建事件发布（{@link #publishUserCreated}）</li>
 *   <li>用户禁用事件发布（{@link #publishUserDisabled}）</li>
 *   <li>用户组别变更事件发布（{@link #publishUserGroupChanged}）</li>
 *   <li>角色权限更新事件发布（{@link #publishRolePermissionUpdated}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>用户域 — 领域事件发布</p>
 */
@Service
public class UserDomainEventPublisherApplicationService {

    private static final Logger log = LoggerFactory.getLogger(UserDomainEventPublisherApplicationService.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public UserDomainEventPublisherApplicationService(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishUserCreated(
            UUID userId, String username, String realName,
            UUID roleId, String roleCode,
            UUID groupId, UUID deptId,
            Integer status, UUID operatorId) {
        UUID groupOrDept = groupId != null ? groupId : deptId;
        publish(new UserCreatedEvent(
                UserDomainEventHeaders.newEventId(),
                userId, username, realName,
                roleId, roleCode,
                groupOrDept, deptId,
                UserStatusLabel.fromCode(status),
                operatorId,
                LocalDateTime.now(),
                UserDomainEventHeaders.currentTraceId()));
    }

    public void publishUserDisabled(
            UUID userId, Integer oldStatus, Integer newStatus, UUID operatorId) {
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
            UUID oldGroupId, UUID newGroupId,
            UUID oldDeptId, UUID newDeptId,
            UUID operatorId) {
        publish(new UserGroupChangedEvent(
                UserDomainEventHeaders.newEventId(),
                userId,
                oldGroupId, newGroupId,
                oldDeptId, newDeptId,
                operatorId,
                LocalDateTime.now(),
                UserDomainEventHeaders.currentTraceId()));
    }

    public void publishRolePermissionUpdated(
            UUID roleId, String roleCode,
            String oldPermissionHash, String newPermissionHash,
            UUID operatorId) {
        publish(new RolePermissionUpdatedEvent(
                UserDomainEventHeaders.newEventId(),
                roleId, roleCode,
                oldPermissionHash, newPermissionHash,
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