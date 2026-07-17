package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 当前用户权限策略（DDD-USER-MIGRATION-U6）。
 *
 * <p>负责当前用户上下文中的角色编码解析、self/group/all 数据范围编码、
 * 权限包聚合与操作权限判断。</p>
 */
public class CurrentUserPermissionPolicy {

    private static final Set<String> CANONICAL_ROLE_CODES = Set.of(
            RoleCodes.ADMIN,
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF,
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.CHANNEL_STAFF,
            RoleCodes.OPS_STAFF);

    public record RolePermission(String roleCode, Integer dataScope, Map<String, Object> permissions) {
    }

    public List<String> resolveRoleCodes(List<RolePermission> roles, List<String> requestRoleCodes) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (roles != null) {
            roles.stream()
                    .map(RolePermission::roleCode)
                    .filter(CurrentUserPermissionPolicy::hasText)
                    .forEach(codes::add);
        }
        if (codes.isEmpty() && requestRoleCodes != null) {
            requestRoleCodes.stream()
                    .filter(CurrentUserPermissionPolicy::hasText)
                    .forEach(codes::add);
        }
        return new ArrayList<>(codes);
    }

    public int resolveDataScopeCode(List<RolePermission> roles, DataScope requestScope, List<String> roleCodes) {
        if (hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
            return DataScope.ALL.getCode();
        }
        if (roles != null && !roles.isEmpty()) {
            return roles.stream()
                    .map(RolePermission::dataScope)
                    .filter(scope -> scope != null && scope > 0)
                    .max(Integer::compareTo)
                    .orElse(requestScope == null ? DataScope.PERSONAL.getCode() : requestScope.getCode());
        }
        return requestScope == null ? DataScope.PERSONAL.getCode() : requestScope.getCode();
    }

    public boolean hasAnyRole(Object roleCodes, String... expectedRoles) {
        if (roleCodes == null || expectedRoles == null || expectedRoles.length == 0) {
            return false;
        }
        Set<String> expected = new LinkedHashSet<>(normalizeRoleCodes(Arrays.asList(expectedRoles)));
        if (expected.isEmpty()) {
            return false;
        }
        return normalizeRoleCodes(roleCodes).stream().anyMatch(expected::contains);
    }

    /**
     * 判断当前账号在系统内置角色中是否只有指定角色。
     *
     * <p>自定义菜单角色不参与岗位互斥判断；一旦同时拥有另一个内置业务角色，
     * 就必须按多角色权限并集处理，不能再套用“纯运营/纯招商/纯渠道”的收缩规则。</p>
     */
    public boolean hasOnlyCanonicalRole(Object roleCodes, String expectedRole) {
        String expected = normalizeKey(expectedRole);
        if (!CANONICAL_ROLE_CODES.contains(expected)) {
            return false;
        }
        Set<String> normalized = new LinkedHashSet<>(normalizeRoleCodes(roleCodes));
        if (!normalized.contains(expected)) {
            return false;
        }
        return normalized.stream()
                .filter(CANONICAL_ROLE_CODES::contains)
                .allMatch(expected::equals);
    }

    public List<String> normalizeRoleCodes(Object roleCodes) {
        if (roleCodes == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (roleCodes instanceof Collection<?> collection) {
            collection.stream()
                    .map(item -> normalizeKey(Objects.toString(item, "")))
                    .filter(CurrentUserPermissionPolicy::hasText)
                    .forEach(normalized::add);
            return new ArrayList<>(normalized);
        }
        String raw = Objects.toString(roleCodes, "");
        if (!hasText(raw)) {
            return List.of();
        }
        for (String roleCode : raw.replace("[", "").replace("]", "").split(",")) {
            String normalizedRole = normalizeKey(roleCode);
            if (hasText(normalizedRole)) {
                normalized.add(normalizedRole);
            }
        }
        return new ArrayList<>(normalized);
    }

    public Map<String, Object> mergePermissions(List<RolePermission> roles, int dataScopeCode) {
        LinkedHashSet<String> menus = new LinkedHashSet<>();
        Map<String, LinkedHashSet<String>> operations = new LinkedHashMap<>();
        if (roles != null) {
            for (RolePermission role : roles) {
                Map<String, Object> permissions = role.permissions();
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

    public CheckPermissionResponse checkPermission(
            List<String> requestRoleCodes,
            List<RolePermission> roles,
            CheckPermissionRequest request) {
        String resource = normalizeKey(request.resource());
        String action = normalizeKey(request.action());
        if (hasAdminRole(requestRoleCodes)) {
            return new CheckPermissionResponse(resource, action, true);
        }
        return new CheckPermissionResponse(resource, action, permissionAllows(roles, resource, action));
    }

    public String scopeName(int code) {
        if (code == DataScope.ALL.getCode()) {
            return "all";
        }
        if (code == DataScope.DEPT.getCode()) {
            return "group";
        }
        return "self";
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

    private boolean permissionAllows(List<RolePermission> roles, String resource, String action) {
        if (!hasText(resource) || !hasText(action)) {
            return false;
        }
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (RolePermission role : roles) {
            Map<String, Object> permissions = role.permissions();
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
        return hasAnyRole(roleCodes, RoleCodes.ADMIN);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
