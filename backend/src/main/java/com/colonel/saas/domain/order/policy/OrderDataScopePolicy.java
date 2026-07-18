package com.colonel.saas.domain.order.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 订单数据页的角色归属策略。
 *
 * <p>该策略只解释角色与订单归属列的映射，不执行查询，也不改变其他数据域的数据范围规则。</p>
 */
public final class OrderDataScopePolicy {

    public Scope resolve(Collection<String> roleCodes) {
        if (hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF)) {
            return new Scope("user_id", "dept_id", true);
        }
        if (hasAnyRole(roleCodes, RoleCodes.BIZ_LEADER)) {
            return new Scope("colonel_user_id", "dept_id", false);
        }
        if (hasAnyRole(roleCodes, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF)) {
            return new Scope("channel_user_id", "channel_dept_id", false);
        }
        return new Scope("user_id", "dept_id", false);
    }

    /** 招商专员的订单明细及其业绩补充是全量只读业务视图。 */
    public DataScope resolveOrderDetailPerformanceScope(
            DataScope dataScope,
            Collection<String> roleCodes) {
        return hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF) ? DataScope.ALL : dataScope;
    }

    /** 角色集合参与缓存键，避免不同角色命中同一份范围缓存。 */
    public String roleCodesCacheKey(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return "NO_ROLES";
        }
        List<String> normalized = roleCodes.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        return normalized.isEmpty() ? "NO_ROLES" : String.join(",", normalized);
    }

    private boolean hasAnyRole(Collection<String> roleCodes, String... expectedRoles) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        for (String roleCode : roleCodes) {
            for (String expectedRole : expectedRoles) {
                if (expectedRole.equalsIgnoreCase(roleCode)) {
                    return true;
                }
            }
        }
        return false;
    }

    public record Scope(String ownerColumn, String deptColumn, boolean fullReadOnly) {
    }
}
