package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class UserDomainService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;

    public UserDomainService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            PasswordEncoder passwordEncoder,
            OperationLogService operationLogService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
    }

    public CurrentUserResponse getCurrentUser(
            UUID userId,
            UUID deptId,
            DataScope requestScope,
            List<String> requestRoleCodes) {
        SysUser user = requireLoginEligibleUser(userId);
        List<SysRole> roles = activeRoles(userId);
        List<String> roleCodes = resolveRoleCodes(roles, requestRoleCodes);
        int dataScopeCode = resolveDataScopeCode(roles, requestScope, roleCodes);
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId() == null ? deptId : user.getDeptId(),
                dataScopeCode,
                scopeName(dataScopeCode),
                roleCodes,
                mergePermissions(roles, dataScopeCode),
                user.getStatus() == null ? SysUserStatus.ACTIVE : user.getStatus(),
                Boolean.TRUE.equals(user.getForcePasswordChange())
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        SysUser user = requireLoginEligibleUser(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw BusinessException.forbidden("原密码错误");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.newPassword()));
        if (SysUserStatus.isPendingActivation(user.getStatus())) {
            update.setStatus(SysUserStatus.ACTIVE);
        }
        update.setForcePasswordChange(false);
        sysUserMapper.updateById(update);
        operationLogService.recordSystemAction(
                userId,
                "用户域",
                "修改密码",
                "PUT",
                "SysUser",
                userId.toString(),
                user.getUsername(),
                "用户修改自己的登录密码"
        );
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
            wrapper.eq("deleted", 0)
                    .eq("status", 1)
                    .eq("dept_id", deptId);
            List<UUID> userIds = sysUserMapper.selectList(wrapper).stream()
                    .map(SysUser::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return new UserDataScopeResponse("group", DataScope.DEPT.getCode(), userIds);
        }
        return new UserDataScopeResponse("self", DataScope.PERSONAL.getCode(), List.of(userId));
    }

    public CheckPermissionResponse checkPermission(
            UUID userId,
            List<String> requestRoleCodes,
            CheckPermissionRequest request) {
        String resource = normalizeKey(request.resource());
        String action = normalizeKey(request.action());
        if (hasAdminRole(requestRoleCodes)) {
            return new CheckPermissionResponse(resource, action, true);
        }
        boolean allowed = permissionAllows(activeRoles(userId), resource, action);
        return new CheckPermissionResponse(resource, action, allowed);
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

    private List<String> resolveRoleCodes(List<SysRole> roles, List<String> requestRoleCodes) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (roles != null) {
            roles.stream()
                    .map(SysRole::getRoleCode)
                    .filter(StringUtils::hasText)
                    .forEach(codes::add);
        }
        if (codes.isEmpty() && requestRoleCodes != null) {
            requestRoleCodes.stream()
                    .filter(StringUtils::hasText)
                    .forEach(codes::add);
        }
        return new ArrayList<>(codes);
    }

    private int resolveDataScopeCode(List<SysRole> roles, DataScope requestScope, List<String> roleCodes) {
        if (roleCodes != null && (roleCodes.contains(RoleCodes.ADMIN) || roleCodes.contains(RoleCodes.OPS_STAFF))) {
            return DataScope.ALL.getCode();
        }
        if (roles != null && !roles.isEmpty()) {
            return roles.stream()
                    .map(SysRole::getDataScope)
                    .filter(scope -> scope != null && scope > 0)
                    .max(Integer::compareTo)
                    .orElse(requestScope == null ? DataScope.PERSONAL.getCode() : requestScope.getCode());
        }
        return requestScope == null ? DataScope.PERSONAL.getCode() : requestScope.getCode();
    }

    private Map<String, Object> mergePermissions(List<SysRole> roles, int dataScopeCode) {
        LinkedHashSet<String> menus = new LinkedHashSet<>();
        Map<String, LinkedHashSet<String>> operations = new LinkedHashMap<>();
        if (roles != null) {
            for (SysRole role : roles) {
                Map<String, Object> permissions = role.getPermissions();
                if (permissions == null || permissions.isEmpty()) {
                    continue;
                }
                addValues(menus, permissions.get("menus"));
                mergeOperations(operations, permissions.get("operations"));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menus", new ArrayList<>(menus));
        Map<String, List<String>> operationResult = new LinkedHashMap<>();
        operations.forEach((resource, actions) -> operationResult.put(resource, new ArrayList<>(actions)));
        result.put("operations", operationResult);
        result.put("data_scope", scopeName(dataScopeCode));
        return result;
    }

    private void mergeOperations(Map<String, LinkedHashSet<String>> target, Object rawOperations) {
        if (!(rawOperations instanceof Map<?, ?> rawMap)) {
            return;
        }
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String resource = normalizeKey(Objects.toString(entry.getKey(), ""));
            if (resource.isBlank()) {
                continue;
            }
            LinkedHashSet<String> actions = target.computeIfAbsent(resource, key -> new LinkedHashSet<>());
            addValues(actions, entry.getValue());
        }
    }

    private void addValues(Set<String> target, Object raw) {
        if (raw instanceof Collection<?> collection) {
            for (Object value : collection) {
                String text = normalizeKey(Objects.toString(value, ""));
                if (!text.isBlank()) {
                    target.add(text);
                }
            }
            return;
        }
        String text = normalizeKey(Objects.toString(raw, ""));
        if (!text.isBlank()) {
            target.add(text);
        }
    }

    private boolean permissionAllows(List<SysRole> roles, String resource, String action) {
        if (!StringUtils.hasText(resource) || !StringUtils.hasText(action)) {
            return false;
        }
        for (SysRole role : roles) {
            Map<String, Object> permissions = role.getPermissions();
            if (permissions == null || permissions.isEmpty()) {
                continue;
            }
            if (operationAllows(permissions.get("operations"), resource, action)) {
                return true;
            }
        }
        return false;
    }

    private boolean operationAllows(Object rawOperations, String resource, String action) {
        if (!(rawOperations instanceof Map<?, ?> rawMap)) {
            return false;
        }
        return actionAllowed(rawMap.get(resource), action)
                || actionAllowed(rawMap.get("*"), action);
    }

    private boolean actionAllowed(Object rawActions, String action) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        addValues(actions, rawActions);
        return actions.contains("*") || actions.contains(action);
    }

    private boolean hasAdminRole(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return roleCodes.stream().anyMatch(RoleCodes.ADMIN::equals);
    }

    private String scopeName(int code) {
        if (code == DataScope.ALL.getCode()) {
            return "all";
        }
        if (code == DataScope.DEPT.getCode()) {
            return "group";
        }
        return "self";
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
