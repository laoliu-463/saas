package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.auth.service.OrgStructureService.ResolvedAssignment;
import com.colonel.saas.auth.service.OrgStructureService.SplitAssignment;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
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

    private final SysUserMapper sysUserMapper;
    private final OperationLogService operationLogService;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final UserPermissionCacheService userPermissionCacheService;
    private final OrgStructureService orgStructureService;
    private final AuthorizationVersionApplicationService authorizationVersionService;

    public SysUserGroupMembershipApplication(
            SysUserMapper sysUserMapper,
            OperationLogService operationLogService,
            UserDomainEventPublisher userDomainEventPublisher,
            UserPermissionCacheService userPermissionCacheService,
            OrgStructureService orgStructureService,
            AuthorizationVersionApplicationService authorizationVersionService) {
        this.sysUserMapper = sysUserMapper;
        this.operationLogService = operationLogService;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.userPermissionCacheService = userPermissionCacheService;
        this.orgStructureService = orgStructureService;
        this.authorizationVersionService = authorizationVersionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignUsersToGroup(UUID groupId, List<UUID> userIds, UUID currentUserId) {
        ResolvedAssignment groupAssignment = orgStructureService.resolveAssignment(null, groupId);
        for (UUID targetUserId : userIds) {
            SysUser user = requireUser(targetUserId);
            UUID previousDeptId = user.getDeptId();
            user.setDeptId(groupAssignment.effectiveDeptId());
            sysUserMapper.updateById(user);
            recordOrgChangeIfNeeded(user, previousDeptId, user.getDeptId(), currentUserId);
            if (deptChanged(previousDeptId, user.getDeptId())) {
                authorizationVersionService.incrementUser(
                        user.getId(),
                        "USER_GROUP_MEMBERSHIP_UPDATED",
                        currentUserId);
            }
            userPermissionCacheService.invalidateUser(user.getId());
            userPermissionCacheService.invalidateDataScopeForGroupChange(previousDeptId, user.getDeptId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeUsersFromGroup(UUID groupId, List<UUID> userIds, UUID currentUserId) {
        for (UUID targetUserId : userIds) {
            SysUser user = requireUser(targetUserId);
            if (!Objects.equals(user.getDeptId(), groupId)) {
                continue;
            }
            UUID previousDeptId = user.getDeptId();
            user.setDeptId(null);
            sysUserMapper.updateById(user);
            recordOrgChangeIfNeeded(user, previousDeptId, null, currentUserId);
            if (deptChanged(previousDeptId, user.getDeptId())) {
                authorizationVersionService.incrementUser(
                        user.getId(),
                        "USER_GROUP_MEMBERSHIP_UPDATED",
                        currentUserId);
            }
            userPermissionCacheService.invalidateUser(user.getId());
            userPermissionCacheService.invalidateDataScopeForGroupChange(previousDeptId, null);
        }
    }

    private SysUser requireUser(UUID id) {
        return sysUserMapper.findActiveById(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    private boolean deptChanged(UUID previousDeptId, UUID newDeptId) {
        return !Objects.equals(previousDeptId, newDeptId);
    }

    private void recordOrgChangeIfNeeded(
            SysUser user,
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
                user.getId().toString(),
                user.getUsername(),
                orgStructureService.formatOrgChangeRemark(
                        user.getId(),
                        previousEffectiveDeptId,
                        newEffectiveDeptId,
                        operatorId));
        userDomainEventPublisher.publishUserGroupChanged(
                user.getId(),
                oldSplit.groupId(),
                newSplit.groupId(),
                oldSplit.parentDeptId(),
                newSplit.parentDeptId(),
                operatorId);
    }
}
