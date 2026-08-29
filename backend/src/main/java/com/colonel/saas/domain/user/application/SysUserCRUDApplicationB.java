package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserResetPasswordRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.auth.service.OrgStructureService.ResolvedAssignment;
import com.colonel.saas.auth.service.OrgStructureService.SplitAssignment;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.policy.UserAccessPolicy.AccessibleUser;
import com.colonel.saas.domain.user.port.UserCrudMutationStore;
import com.colonel.saas.domain.user.port.UserCrudMutationStore.ManagedUser;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.service.UserPermissionCacheService;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 系统用户 CRUD 应用服务 B（DDD-USER-MIGRATION-013，Issue #22）。
 *
 * <p>本切片只承接 update / delete / resetPassword 三个用例，
 * 暂不接入 Controller 或替换旧 Service 生产路径。</p>
 */
@Service
public class SysUserCRUDApplicationB {

    private final UserCrudMutationStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final UserPermissionCacheService userPermissionCacheService;
    private final OrgStructureService orgStructureService;
    private final UserAccessPolicy userAccessPolicy;
    private final AuthorizationVersionApplicationService authorizationVersionService;

    public SysUserCRUDApplicationB(
            UserCrudMutationStore userStore,
            PasswordEncoder passwordEncoder,
            OperationLogService operationLogService,
            UserDomainEventPublisher userDomainEventPublisher,
            UserPermissionCacheService userPermissionCacheService,
            OrgStructureService orgStructureService,
            UserAccessPolicy userAccessPolicy,
            AuthorizationVersionApplicationService authorizationVersionService) {
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.userPermissionCacheService = userPermissionCacheService;
        this.orgStructureService = orgStructureService;
        this.userAccessPolicy = userAccessPolicy;
        this.authorizationVersionService = authorizationVersionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public SysUserVO update(UUID id, SysUserUpdateRequest request, UUID currentUserId, DataScope dataScope) {
        ManagedUser user = requireUser(id);
        userAccessPolicy.assertCanAccess(accessibleUser(user), currentUserId, dataScope);

        Integer previousStatus = user.status();
        UUID previousDeptId = user.deptId();

        Integer newStatus = user.status();
        if (request.status() != null) {
            newStatus = request.status();
        }
        UUID newDeptId = user.deptId();
        if (request.parentDeptId() != null || request.groupId() != null || request.deptId() != null) {
            ResolvedAssignment assignment = resolveAssignment(
                    request.parentDeptId(),
                    request.groupId(),
                    request.deptId());
            newDeptId = assignment.effectiveDeptId();
        }
        ManagedUser updatedUser = user.withProfile(
                request.realName(),
                request.phone(),
                request.email(),
                newDeptId,
                newStatus);
        userStore.saveUser(updatedUser);
        if (!Objects.equals(previousStatus, updatedUser.status())
                || !Objects.equals(previousDeptId, updatedUser.deptId())) {
            authorizationVersionService.incrementUser(
                    id,
                    "USER_AUTHORIZATION_CONTEXT_UPDATED",
                    currentUserId);
        }
        recordOrgChangeIfNeeded(updatedUser, previousDeptId, updatedUser.deptId(), currentUserId);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "更新用户",
                "PUT",
                "SysUser",
                updatedUser.id().toString(),
                updatedUser.username(),
                "更新用户: " + updatedUser.username());
        if (becameDisabled(previousStatus, updatedUser.status())) {
            userDomainEventPublisher.publishUserDisabled(
                    updatedUser.id(),
                    previousStatus,
                    updatedUser.status(),
                    currentUserId);
        }
        userPermissionCacheService.invalidateUser(updatedUser.id());
        userPermissionCacheService.invalidateDataScopeForGroupChange(previousDeptId, updatedUser.deptId());
        return orgStructureService.enrichUser(toVO(updatedUser));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID currentUserId, DataScope dataScope) {
        if (id.equals(currentUserId)) {
            throw BusinessException.stateInvalid("不能删除当前登录用户");
        }
        ManagedUser user = requireUser(id);
        userAccessPolicy.assertCanAccess(accessibleUser(user), currentUserId, dataScope);
        userStore.deleteUserRoles(id);
        userStore.softDeleteUser(id);
        userPermissionCacheService.invalidateUser(id);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "删除用户",
                "DELETE",
                "SysUser",
                user.id().toString(),
                user.username(),
                "删除用户: " + user.username());
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(
            UUID id,
            SysUserResetPasswordRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        ManagedUser user = requireUser(id);
        userAccessPolicy.assertCanAccess(accessibleUser(user), currentUserId, dataScope);
        userStore.updatePassword(id, passwordEncoder.encode(request.newPassword()), true);
        authorizationVersionService.incrementUser(
                id,
                "USER_PASSWORD_RESET",
                currentUserId);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "重置密码",
                "PUT",
                "SysUser",
                user.id().toString(),
                user.username(),
                "重置用户密码: " + user.username());
    }

    private ManagedUser requireUser(UUID id) {
        return userStore.findUser(id)
                .filter(user -> user.deleted() == null || user.deleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    private static AccessibleUser accessibleUser(ManagedUser user) {
        return new AccessibleUser(user.id(), user.deptId());
    }

    private SysUserVO toVO(ManagedUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.id());
        vo.setUsername(user.username());
        vo.setRealName(user.realName());
        vo.setPhone(user.phone());
        vo.setEmail(user.email());
        vo.setDeptId(user.deptId());
        vo.setStatus(user.status());
        vo.setForcePasswordChange(user.forcePasswordChange());
        vo.setLastLoginAt(user.lastLoginAt());
        vo.setCreateTime(user.createTime());
        List<UUID> roleIds = userStore.findRoleIdsByUserId(user.id());
        vo.setRoleIds(roleIds);
        return vo;
    }

    private ResolvedAssignment resolveAssignment(UUID parentDeptId, UUID groupId, UUID legacyDeptId) {
        if (parentDeptId != null || groupId != null) {
            return orgStructureService.resolveAssignment(parentDeptId, groupId);
        }
        if (legacyDeptId != null) {
            SplitAssignment split = orgStructureService.splitAssignment(legacyDeptId);
            return new ResolvedAssignment(
                    legacyDeptId,
                    split.parentDeptId() != null ? split.parentDeptId() : legacyDeptId,
                    split.groupId());
        }
        return new ResolvedAssignment(null, null, null);
    }

    private boolean becameDisabled(Integer previousStatus, Integer newStatus) {
        if (newStatus == null || newStatus != SysUserStatus.DISABLED) {
            return false;
        }
        return previousStatus == null || previousStatus != SysUserStatus.DISABLED;
    }

    private boolean deptChanged(UUID previousDeptId, UUID newDeptId) {
        return !Objects.equals(previousDeptId, newDeptId);
    }

    private void recordOrgChangeIfNeeded(
            ManagedUser user,
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
