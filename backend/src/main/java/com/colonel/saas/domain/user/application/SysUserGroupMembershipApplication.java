package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.auth.service.OrgStructureService.ResolvedAssignment;
import com.colonel.saas.auth.service.OrgStructureService.SplitAssignment;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.user.port.UserGroupMembershipStore;
import com.colonel.saas.domain.user.port.UserGroupMembershipStore.GroupMember;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.service.UserPermissionCacheService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 系统用户业务组成员应用服务（DDD-USER-MIGRATION-014）。
 *
 * <p>承接 {@code SysUserService.assignUsersToGroup} 与
 * {@code SysUserService.removeUsersFromGroup} 的组织成员变更逻辑。</p>
 */
@Service
public class SysUserGroupMembershipApplication {

    private final UserGroupMembershipStore userGroupMembershipStore;
    private final OperationLogService operationLogService;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final UserPermissionCacheService userPermissionCacheService;
    private final OrgStructureService orgStructureService;

    public SysUserGroupMembershipApplication(
            UserGroupMembershipStore userGroupMembershipStore,
            OperationLogService operationLogService,
            UserDomainEventPublisher userDomainEventPublisher,
            UserPermissionCacheService userPermissionCacheService,
            OrgStructureService orgStructureService) {
        this.userGroupMembershipStore = userGroupMembershipStore;
        this.operationLogService = operationLogService;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.userPermissionCacheService = userPermissionCacheService;
        this.orgStructureService = orgStructureService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignUsersToGroup(UUID groupId, List<UUID> userIds, UUID currentUserId) {
        ResolvedAssignment groupAssignment = orgStructureService.resolveAssignment(null, groupId);
        for (UUID targetUserId : userIds) {
            GroupMember user = requireUser(targetUserId);
            UUID previousDeptId = user.deptId();
            GroupMember updatedUser = user.withDept(groupAssignment.effectiveDeptId());
            userGroupMembershipStore.updateDept(updatedUser.id(), updatedUser.deptId());
            recordOrgChangeIfNeeded(updatedUser, previousDeptId, updatedUser.deptId(), currentUserId);
            userPermissionCacheService.invalidateUser(updatedUser.id());
            userPermissionCacheService.invalidateDataScopeForGroupChange(previousDeptId, updatedUser.deptId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeUsersFromGroup(UUID groupId, List<UUID> userIds, UUID currentUserId) {
        for (UUID targetUserId : userIds) {
            GroupMember user = requireUser(targetUserId);
            if (!Objects.equals(user.deptId(), groupId)) {
                continue;
            }
            UUID previousDeptId = user.deptId();
            GroupMember updatedUser = user.withDept(null);
            userGroupMembershipStore.updateDept(updatedUser.id(), null);
            recordOrgChangeIfNeeded(updatedUser, previousDeptId, null, currentUserId);
            userPermissionCacheService.invalidateUser(updatedUser.id());
            userPermissionCacheService.invalidateDataScopeForGroupChange(previousDeptId, null);
        }
    }

    private GroupMember requireUser(UUID id) {
        return userGroupMembershipStore.findMember(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    private boolean deptChanged(UUID previousDeptId, UUID newDeptId) {
        return !Objects.equals(previousDeptId, newDeptId);
    }

    private void recordOrgChangeIfNeeded(
            GroupMember user,
            UUID previousEffectiveDeptId,
            UUID newEffectiveDeptId,
            UUID operatorId) {
        if (!deptChanged(previousEffectiveDeptId, newEffectiveDeptId)) {
            return;
        }
        SplitAssignment oldSplit = orgStructureService.splitAssignment(previousEffectiveDeptId);
        SplitAssignment newSplit = orgStructureService.splitAssignment(newEffectiveDeptId);
        operationLogService.recordSystemAction(
                operatorId,
                "用户管理",
                "组织归属变更",
                "PUT",
                "SysUser",
                user.id().toString(),
                user.username(),
                orgStructureService.formatOrgChangeRemark(
                        user.id(),
                        previousEffectiveDeptId,
                        newEffectiveDeptId,
                        operatorId));
        userDomainEventPublisher.publishUserGroupChanged(
                user.id(),
                oldSplit.groupId(),
                newSplit.groupId(),
                oldSplit.parentDeptId(),
                newSplit.parentDeptId(),
                operatorId);
    }
}
