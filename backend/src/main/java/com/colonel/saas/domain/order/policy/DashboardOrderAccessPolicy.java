package com.colonel.saas.domain.order.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Dashboard 订单事实的访问策略。
 *
 * <p>该策略只负责把角色和数据范围转换成订单可见性及订单 SQL 条件，
 * 不执行查询、不持有 Mapper，也不改变旧的数据范围语义。</p>
 */
public class DashboardOrderAccessPolicy {

    private final DataScopeResolver dataScopeResolver;
    private final DddRefactorProperties dddRefactorProperties;
    private final CurrentUserPermissionChecker currentUserPermissionChecker;

    public DashboardOrderAccessPolicy(
            DataScopeResolver dataScopeResolver,
            DddRefactorProperties dddRefactorProperties) {
        this(
                dataScopeResolver,
                dddRefactorProperties,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()));
    }

    DashboardOrderAccessPolicy(
            DataScopeResolver dataScopeResolver,
            DddRefactorProperties dddRefactorProperties,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        this.dataScopeResolver = Objects.requireNonNull(dataScopeResolver, "dataScopeResolver");
        this.dddRefactorProperties = Objects.requireNonNull(dddRefactorProperties, "dddRefactorProperties");
        this.currentUserPermissionChecker = Objects.requireNonNull(
                currentUserPermissionChecker,
                "currentUserPermissionChecker");
    }

    /**
     * 为 Dashboard 的原始订单 SQL 追加渠道角色条件。
     *
     * @return true 表示已经按角色追加条件，调用方无需再追加普通 dataScope 条件
     */
    public boolean appendChannelRoleScope(
            List<String> clauses,
            List<Object> args,
            UUID userId,
            UUID deptId,
            List<String> roleCodes) {
        if (currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.CHANNEL_STAFF)) {
            clauses.add("co.channel_user_id = ?");
            args.add(userId);
            return true;
        }
        if (currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.CHANNEL_LEADER)) {
            if (deptId == null) {
                clauses.add("1 = 0");
            } else {
                clauses.add("co.channel_user_id IN (SELECT id FROM sys_user WHERE dept_id = ?)");
                args.add(deptId);
            }
            return true;
        }
        return false;
    }

    /**
     * 将角色和 dataScope 解释为订单事实可见性。
     */
    public OrderReadFacade.OrderVisibility resolveOrderVisibility(
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            List<String> roleCodes) {
        if (currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
            return OrderReadFacade.OrderVisibility.all();
        }
        if (currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.CHANNEL_STAFF)) {
            return OrderReadFacade.OrderVisibility.channelUser(userId);
        }
        if (currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.CHANNEL_LEADER)) {
            return OrderReadFacade.OrderVisibility.channelDept(deptId);
        }
        if (dataScope == null) {
            return OrderReadFacade.OrderVisibility.all();
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            return resolveLegacyVisibility(userId, deptId, dataScope);
        }
        DataScopeResolver.ResolvedDataScope resolved =
                dataScopeResolver.resolve(userId, deptId, dataScope);
        if (resolved.filtersUser()) {
            return OrderReadFacade.OrderVisibility.user(userId);
        }
        if (resolved.filtersDept()) {
            return OrderReadFacade.OrderVisibility.dept(deptId);
        }
        return dataScopeResolver.requiresFilter(dataScope)
                ? OrderReadFacade.OrderVisibility.none()
                : OrderReadFacade.OrderVisibility.all();
    }

    private OrderReadFacade.OrderVisibility resolveLegacyVisibility(
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (dataScope == DataScope.PERSONAL && userId != null) {
            return OrderReadFacade.OrderVisibility.user(userId);
        }
        if (dataScope == DataScope.DEPT && deptId != null) {
            return OrderReadFacade.OrderVisibility.dept(deptId);
        }
        if (dataScope == DataScope.PERSONAL || dataScope == DataScope.DEPT) {
            return OrderReadFacade.OrderVisibility.none();
        }
        return OrderReadFacade.OrderVisibility.all();
    }
}
