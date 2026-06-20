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
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
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
import java.util.stream.Collectors;

/**
 * 系统用户 CRUD 应用服务 B（DDD-USER-MIGRATION-013，Issue #22）。
 *
 * <p>本切片只承接 update / delete / resetPassword 三个用例，
 * 暂不接入 Controller 或替换旧 Service 生产路径。</p>
 */
@Service
public class SysUserCRUDApplicationB {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final UserPermissionCacheService userPermissionCacheService;
    private final OrgStructureService orgStructureService;
    private final UserAccessPolicy userAccessPolicy;

    public SysUserCRUDApplicationB(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            PasswordEncoder passwordEncoder,
            OperationLogService operationLogService,
            UserDomainEventPublisher userDomainEventPublisher,
            UserPermissionCacheService userPermissionCacheService,
            OrgStructureService orgStructureService,
            UserAccessPolicy userAccessPolicy) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.userPermissionCacheService = userPermissionCacheService;
        this.orgStructureService = orgStructureService;
        this.userAccessPolicy = userAccessPolicy;
    }

    public SysUserVO update(UUID id, SysUserUpdateRequest request, UUID currentUserId, DataScope dataScope) {
        SysUser user = requireUser(id);
        userAccessPolicy.assertCanAccess(accessibleUser(user), currentUserId, dataScope);

        Integer previousStatus = user.getStatus();
        UUID previousDeptId = user.getDeptId();

        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.parentDeptId() != null || request.groupId() != null || request.deptId() != null) {
            ResolvedAssignment assignment = resolveAssignment(
                    request.parentDeptId(),
                    request.groupId(),
                    request.deptId());
            user.setDeptId(assignment.effectiveDeptId());
        }
        sysUserMapper.updateById(user);
        recordOrgChangeIfNeeded(user, previousDeptId, user.getDeptId(), currentUserId);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "更新用户",
                "PUT",
                "SysUser",
                user.getId().toString(),
                user.getUsername(),
                "更新用户: " + user.getUsername());
        if (becameDisabled(previousStatus, user.getStatus())) {
            userDomainEventPublisher.publishUserDisabled(
                    user.getId(),
                    previousStatus,
                    user.getStatus(),
                    currentUserId);
        }
        userPermissionCacheService.invalidateUser(user.getId());
        userPermissionCacheService.invalidateDataScopeForGroupChange(previousDeptId, user.getDeptId());
        return orgStructureService.enrichUser(toVO(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID currentUserId, DataScope dataScope) {
        if (id.equals(currentUserId)) {
            throw BusinessException.stateInvalid("不能删除当前登录用户");
        }
        SysUser user = requireUser(id);
        userAccessPolicy.assertCanAccess(accessibleUser(user), currentUserId, dataScope);
        sysUserRoleMapper.deleteByUserIdPhysical(id);
        sysUserMapper.softDeleteById(id);
        userPermissionCacheService.invalidateUser(id);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "删除用户",
                "DELETE",
                "SysUser",
                user.getId().toString(),
                user.getUsername(),
                "删除用户: " + user.getUsername());
    }

    public void resetPassword(
            UUID id,
            SysUserResetPasswordRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        userAccessPolicy.assertCanAccess(accessibleUser(user), currentUserId, dataScope);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(request.newPassword()));
        update.setForcePasswordChange(true);
        sysUserMapper.updateById(update);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "重置密码",
                "PUT",
                "SysUser",
                user.getId().toString(),
                user.getUsername(),
                "重置用户密码: " + user.getUsername());
    }

    private SysUser requireUser(UUID id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    private static AccessibleUser accessibleUser(SysUser user) {
        return new AccessibleUser(user.getId(), user.getDeptId());
    }

    private SysUserVO toVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setForcePasswordChange(user.getForcePasswordChange());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreateTime(user.getCreateTime());
        List<UUID> roleIds = sysUserRoleMapper.findByUserId(user.getId()).stream()
                .map(relation -> relation.getRoleId())
                .collect(Collectors.toList());
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
