package com.colonel.saas.domain.user.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy.RolePermission;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy.CredentialUser;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy.PasswordChangeUpdate;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.service.OperationLogService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 当前用户应用服务（DDD-USER-MIGRATION-006 自包含实现）。
 *
 * <p>收口 {@code /users/current} 的用户上下文、数据范围、权限检查和密码修改入口。
 * 业务逻辑在本类，不依赖 {@code UserDomainService}。委派壳 {@code UserDomainService}
 * 保留旧签名以兼容遗留调用方。</p>
 */
@Service
public class CurrentUserApplicationService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;
    private final UserCredentialPolicy userCredentialPolicy;

    public CurrentUserApplicationService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            PasswordEncoder passwordEncoder,
            OperationLogService operationLogService,
            CurrentUserPermissionPolicy currentUserPermissionPolicy,
            UserCredentialPolicy userCredentialPolicy) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.currentUserPermissionPolicy = currentUserPermissionPolicy;
        this.userCredentialPolicy = userCredentialPolicy;
    }

    public CurrentUserResponse currentUser(
            UUID userId, UUID deptId, DataScope dataScope, List<String> roleCodes) {
        return getCurrentUser(userId, deptId, dataScope, roleCodes);
    }

    public CurrentUserResponse getCurrentUser(
            UUID userId, UUID deptId, DataScope requestScope, List<String> requestRoleCodes) {
        SysUser user = requireLoginEligibleUser(userId);
        List<RolePermission> roles = activeRolePermissions(userId);
        List<String> roleCodes = currentUserPermissionPolicy.resolveRoleCodes(roles, requestRoleCodes);
        int dataScopeCode = currentUserPermissionPolicy.resolveDataScopeCode(roles, requestScope, roleCodes);
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId() == null ? deptId : user.getDeptId(),
                dataScopeCode,
                currentUserPermissionPolicy.scopeName(dataScopeCode),
                roleCodes,
                currentUserPermissionPolicy.mergePermissions(roles, dataScopeCode),
                user.getStatus() == null ? SysUserStatus.ACTIVE : user.getStatus(),
                Boolean.TRUE.equals(user.getForcePasswordChange())
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        SysUser user = requireLoginEligibleUser(userId);
        userCredentialPolicy.assertOldPasswordMatched(
                passwordEncoder.matches(request.oldPassword(), user.getPassword()));
        CredentialUser credentialUser = new CredentialUser(user.getStatus(), user.getUsername());
        PasswordChangeUpdate passwordChangeUpdate = userCredentialPolicy.buildPasswordChangeUpdate(
                userId,
                credentialUser,
                passwordEncoder.encode(request.newPassword()));
        sysUserMapper.updateById(passwordChangeEntity(passwordChangeUpdate));
        UserCredentialPolicy.PasswordChangeAudit audit = userCredentialPolicy.passwordChangeAudit(userId, credentialUser);
        operationLogService.recordSystemAction(
                audit.userId(), audit.domain(), audit.action(), audit.method(),
                audit.entityType(), audit.entityId(), audit.entityName(), audit.description()
        );
    }

    public UserDataScopeResponse dataScope(UUID userId, UUID deptId, DataScope dataScope) {
        return getUserDataScope(userId, deptId, dataScope);
    }

    public UserDataScopeResponse getUserDataScope(UUID userId, UUID deptId, DataScope dataScope) {
        DataScope resolved = dataScope == null ? DataScope.PERSONAL : dataScope;
        if (resolved == DataScope.ALL) {
            return new UserDataScopeResponse("all", DataScope.ALL.getCode(), Collections.emptyList());
        }
        if (resolved == DataScope.DEPT) {
            if (deptId == null) {
                return new UserDataScopeResponse("group", DataScope.DEPT.getCode(), List.of(userId));
            }
            QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
            wrapper.eq("deleted", 0).eq("status", 1).eq("dept_id", deptId);
            List<UUID> userIds = sysUserMapper.selectList(wrapper).stream()
                    .map(SysUser::getId).filter(Objects::nonNull).distinct().toList();
            return new UserDataScopeResponse("group", DataScope.DEPT.getCode(), userIds);
        }
        return new UserDataScopeResponse("self", DataScope.PERSONAL.getCode(), List.of(userId));
    }

    public CheckPermissionResponse checkPermission(
            UUID userId, List<String> roleCodes, CheckPermissionRequest request) {
        List<String> safeRoleCodes = roleCodes == null ? Collections.emptyList() : roleCodes;
        return currentUserPermissionPolicy.checkPermission(safeRoleCodes, activeRolePermissions(userId), request);
    }

    private SysUser requireLoginEligibleUser(UUID userId) {
        if (userId == null) {
            throw BusinessException.forbidden("无法识别当前用户");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getDeleted() != null && user.getDeleted() != 0) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!SysUserStatus.canLogin(user.getStatus())) {
            throw BusinessException.forbidden("账号已停用");
        }
        return user;
    }

    private List<SysRole> activeRoles(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<SysRole> roles = sysRoleMapper.findByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream()
                .filter(role -> role != null && (role.getStatus() == null || role.getStatus() == 1))
                .toList();
    }

    private List<RolePermission> activeRolePermissions(UUID userId) {
        return activeRoles(userId).stream()
                .map(role -> new RolePermission(role.getRoleCode(), role.getDataScope(), role.getPermissions()))
                .toList();
    }

    private static SysUser passwordChangeEntity(PasswordChangeUpdate passwordChangeUpdate) {
        SysUser update = new SysUser();
        update.setId(passwordChangeUpdate.userId());
        update.setPassword(passwordChangeUpdate.encodedPassword());
        update.setStatus(passwordChangeUpdate.status());
        update.setForcePasswordChange(passwordChangeUpdate.forcePasswordChange());
        return update;
    }
}