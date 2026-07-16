package com.colonel.saas.domain.order.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.entity.ColonelsettlementOrder;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 订单事实的角色化数据范围策略。
 *
 * <p>招商人员按 {@code colonel_user_id}，渠道人员按 {@code channel_user_id} 查询；
 * 两类角色并存时查询两个归因维度的并集。无角色上下文时保留旧 {@code user_id/dept_id}
 * 数据范围路径，避免影响尚未传递角色事实的内部调用。</p>
 */
public final class OrderAccessScope {

    private OrderAccessScope() {
    }

    public static void applyTo(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            OrderAccessContext context,
            CurrentUserPermissionChecker permissionChecker) {
        if (wrapper == null || context == null) {
            return;
        }
        AccessDimension dimension = resolve(context, permissionChecker);
        switch (dimension) {
            case ALL -> {
                return;
            }
            case CHANNEL -> wrapper.eq(ColonelsettlementOrder::getChannelUserId, requireUser(context.userId()));
            case RECRUITER -> wrapper.eq(ColonelsettlementOrder::getColonelUserId, requireUser(context.userId()));
            case BOTH -> wrapper.and(nested -> nested
                    .eq(ColonelsettlementOrder::getChannelUserId, requireUser(context.userId()))
                    .or()
                    .eq(ColonelsettlementOrder::getColonelUserId, requireUser(context.userId())));
            case CHANNEL_DEPT -> wrapper.eq(ColonelsettlementOrder::getChannelDeptId, requireDept(context.deptId()));
            case RECRUITER_DEPT -> applyRecruiterDeptScope(wrapper, requireDept(context.deptId()));
            case BOTH_DEPT -> wrapper.and(nested -> nested
                    .eq(ColonelsettlementOrder::getChannelDeptId, requireDept(context.deptId()))
                    .or()
                    .apply("colonel_user_id IN (SELECT id FROM sys_user WHERE dept_id = {0} AND deleted = 0)",
                            requireDept(context.deptId())));
            case LEGACY_PERSONAL -> wrapper.eq(ColonelsettlementOrder::getUserId, requireUser(context.userId()));
            case LEGACY_DEPT -> wrapper.eq(ColonelsettlementOrder::getDeptId, requireDept(context.deptId()));
        }
    }

    public static void applyTo(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            OrderAccessContext context,
            CurrentUserPermissionChecker permissionChecker) {
        if (wrapper == null || context == null) {
            return;
        }
        AccessDimension dimension = resolve(context, permissionChecker);
        switch (dimension) {
            case ALL -> {
                return;
            }
            case CHANNEL -> wrapper.eq("channel_user_id", requireUser(context.userId()));
            case RECRUITER -> wrapper.eq("colonel_user_id", requireUser(context.userId()));
            case BOTH -> wrapper.and(nested -> nested
                    .eq("channel_user_id", requireUser(context.userId()))
                    .or()
                    .eq("colonel_user_id", requireUser(context.userId())));
            case CHANNEL_DEPT -> wrapper.eq("channel_dept_id", requireDept(context.deptId()));
            case RECRUITER_DEPT -> applyRecruiterDeptScope(wrapper, requireDept(context.deptId()));
            case BOTH_DEPT -> wrapper.and(nested -> nested
                    .eq("channel_dept_id", requireDept(context.deptId()))
                    .or()
                    .apply("colonel_user_id IN (SELECT id FROM sys_user WHERE dept_id = {0} AND deleted = 0)",
                            requireDept(context.deptId())));
            case LEGACY_PERSONAL -> wrapper.eq("user_id", requireUser(context.userId()));
            case LEGACY_DEPT -> wrapper.eq("dept_id", requireDept(context.deptId()));
        }
    }

    /**
     * 对单笔订单详情复用列表/统计的同一角色化归因范围。
     */
    public static boolean canAccess(
            UUID channelUserId,
            UUID recruiterUserId,
            UUID channelDeptId,
            UUID recruiterDeptId,
            UUID legacyUserId,
            UUID legacyDeptId,
            OrderAccessContext context,
            CurrentUserPermissionChecker permissionChecker) {
        if (context == null) {
            return false;
        }
        return switch (resolve(context, permissionChecker)) {
            case ALL -> true;
            case CHANNEL -> Objects.equals(context.userId(), channelUserId);
            case RECRUITER -> Objects.equals(context.userId(), recruiterUserId);
            case BOTH -> Objects.equals(context.userId(), channelUserId)
                    || Objects.equals(context.userId(), recruiterUserId);
            case CHANNEL_DEPT -> Objects.equals(context.deptId(), channelDeptId);
            case RECRUITER_DEPT -> Objects.equals(context.deptId(), recruiterDeptId);
            case BOTH_DEPT -> Objects.equals(context.deptId(), channelDeptId)
                    || Objects.equals(context.deptId(), recruiterDeptId);
            case LEGACY_PERSONAL -> Objects.equals(context.userId(), legacyUserId);
            case LEGACY_DEPT -> Objects.equals(context.deptId(), legacyDeptId);
        };
    }

    private static void applyRecruiterDeptScope(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            UUID deptId) {
        wrapper.apply("colonel_user_id IN (SELECT id FROM sys_user WHERE dept_id = {0} AND deleted = 0)", deptId);
    }

    private static void applyRecruiterDeptScope(QueryWrapper<ColonelsettlementOrder> wrapper, UUID deptId) {
        wrapper.apply("colonel_user_id IN (SELECT id FROM sys_user WHERE dept_id = {0} AND deleted = 0)", deptId);
    }

    private static AccessDimension resolve(
            OrderAccessContext context,
            CurrentUserPermissionChecker permissionChecker) {
        DataScope dataScope = context.dataScope();
        List<String> roles = context.roleCodes();
        if (dataScope == DataScope.ALL || hasAnyRole(permissionChecker, roles, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
            return AccessDimension.ALL;
        }

        boolean channelLeader = hasAnyRole(permissionChecker, roles, RoleCodes.CHANNEL_LEADER);
        boolean recruiterLeader = hasAnyRole(permissionChecker, roles, RoleCodes.BIZ_LEADER);
        if (channelLeader && recruiterLeader) {
            return AccessDimension.BOTH_DEPT;
        }
        if (channelLeader) {
            return AccessDimension.CHANNEL_DEPT;
        }
        if (recruiterLeader) {
            return AccessDimension.RECRUITER_DEPT;
        }

        boolean channelStaff = hasAnyRole(permissionChecker, roles, RoleCodes.CHANNEL_STAFF);
        boolean recruiterStaff = hasAnyRole(permissionChecker, roles, RoleCodes.BIZ_STAFF);
        if (channelStaff && recruiterStaff) {
            return AccessDimension.BOTH;
        }
        if (channelStaff) {
            return AccessDimension.CHANNEL;
        }
        if (recruiterStaff) {
            return AccessDimension.RECRUITER;
        }
        return dataScope == DataScope.DEPT ? AccessDimension.LEGACY_DEPT : AccessDimension.LEGACY_PERSONAL;
    }

    private static boolean hasAnyRole(
            CurrentUserPermissionChecker permissionChecker,
            List<String> roleCodes,
            String... expectedRoles) {
        return permissionChecker != null && permissionChecker.hasAnyRole(roleCodes, expectedRoles);
    }

    private static UUID requireUser(UUID userId) {
        if (userId == null) {
            throw BusinessException.forbidden("数据权限异常：缺少用户上下文");
        }
        return userId;
    }

    private static UUID requireDept(UUID deptId) {
        if (deptId == null) {
            throw BusinessException.forbidden("数据权限异常：缺少部门上下文");
        }
        return deptId;
    }

    private enum AccessDimension {
        ALL,
        CHANNEL,
        RECRUITER,
        BOTH,
        CHANNEL_DEPT,
        RECRUITER_DEPT,
        BOTH_DEPT,
        LEGACY_PERSONAL,
        LEGACY_DEPT
    }
}
