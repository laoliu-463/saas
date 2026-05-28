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
 * 用户域领域事件发布器。
 * <p>
 * 通过 Spring {@link ApplicationEventPublisher} 发布用户域领域事件，
 * 事件在当前事务提交后由监听器异步消费。支持用户创建、禁用、组别变更和角色权限更新四类事件。
 * 发布失败时仅记录警告日志，不影响主业务流程。
 * </p>
 *
 * <ul>
 *     <li>用户创建事件发布（{@link #publishUserCreated}）</li>
 *     <li>用户禁用事件发布（{@link #publishUserDisabled}）</li>
 *     <li>用户组别变更事件发布（{@link #publishUserGroupChanged}）</li>
 *     <li>角色权限更新事件发布（{@link #publishRolePermissionUpdated}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>用户域 — 领域事件发布</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link ApplicationEventPublisher} — Spring 事件发布通道</li>
 *     <li>{@link UserCreatedEvent} — 用户创建事件</li>
 *     <li>{@link UserDisabledEvent} — 用户禁用事件</li>
 *     <li>{@link UserGroupChangedEvent} — 用户组别变更事件</li>
 *     <li>{@link RolePermissionUpdatedEvent} — 角色权限更新事件</li>
 * </ul>
 */
@Service
public class UserDomainEventPublisher {

    /** 日志记录器（手动声明，便于控制类名精确匹配） */
    private static final Logger log = LoggerFactory.getLogger(UserDomainEventPublisher.class);

    /** Spring 事件发布器 */
    private final ApplicationEventPublisher applicationEventPublisher;

    public UserDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 发布用户创建事件。
     * <p>
     * 构建 {@link UserCreatedEvent} 并发布。优先使用 groupId，若为空则回退到 deptId。
     * </p>
     *
     * @param userId    用户 ID
     * @param username  用户名
     * @param realName  真实姓名
     * @param roleId    角色 ID
     * @param roleCode  角色编码
     * @param groupId   组别 ID（可为 null）
     * @param deptId    部门 ID（可为 null）
     * @param status    用户状态码
     * @param operatorId 操作人 ID
     */
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
        // 优先使用 groupId，为空时回退到 deptId
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

    /**
     * 发布用户禁用事件。
     *
     * @param userId    用户 ID
     * @param oldStatus 变更前状态码
     * @param newStatus 变更后状态码
     * @param operatorId 操作人 ID
     */
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

    /**
     * 发布用户组别变更事件。
     *
     * @param userId     用户 ID
     * @param oldGroupId 变更前组别 ID
     * @param newGroupId 变更后组别 ID
     * @param oldDeptId  变更前部门 ID
     * @param newDeptId  变更后部门 ID
     * @param operatorId 操作人 ID
     */
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

    /**
     * 发布角色权限更新事件。
     *
     * @param roleId           角色 ID
     * @param roleCode         角色编码
     * @param oldPermissionHash 变更前权限哈希摘要
     * @param newPermissionHash 变更后权限哈希摘要
     * @param operatorId       操作人 ID
     */
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

    /**
     * 统一事件发布入口，捕获异常确保不影响主流程。
     *
     * @param event 待发布的领域事件
     */
    private void publish(Object event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            // 事件发布失败不影响主业务流程，仅记录警告
            log.warn("用户域事件发布失败，已忽略: eventType={}", event.getClass().getSimpleName(), ex);
        }
    }
}
