package com.colonel.saas.service.performance;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.PerformanceRecord;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 业绩查询数据范围：按角色过滤 final_channel / final_recruiter，禁止越权查看他人业绩。
 */
public final class PerformanceAccessScope {

    private PerformanceAccessScope() {
    }

    public static boolean canExport(PerformanceAccessContext context) {
        if (context == null) {
            return false;
        }
        List<String> roles = context.roleCodes();
        return hasAnyRole(roles, RoleCodes.ADMIN)
                || hasAnyRole(roles, RoleCodes.BIZ_LEADER, RoleCodes.COLONEL_LEADER, RoleCodes.CHANNEL_LEADER);
    }

    public static boolean canRecalculateMonth(PerformanceAccessContext context) {
        return context != null && hasAnyRole(context.roleCodes(), RoleCodes.ADMIN);
    }

    public static void assertFilterAllowed(
            UUID channelId,
            UUID recruiterId,
            PerformanceAccessContext context) {
        if (context == null) {
            throw BusinessException.forbidden("无权访问业绩数据");
        }
        if (isAdminLike(context)) {
            return;
        }
        if (channelId != null && isChannelScoped(context)) {
            assertChannelFilter(channelId, context);
        }
        if (recruiterId != null && isRecruiterScoped(context)) {
            assertRecruiterFilter(recruiterId, context);
        }
    }

    public static boolean canAccessRecord(PerformanceRecord record, PerformanceAccessContext context) {
        if (record == null || context == null) {
            return false;
        }
        if (isAdminLike(context)) {
            return true;
        }
        UUID userId = context.userId();
        if (userId == null) {
            return false;
        }
        if (isChannelStaffOnly(context)) {
            return userId.equals(record.getFinalChannelUserId());
        }
        if (isRecruiterStaffOnly(context)) {
            return userId.equals(record.getFinalRecruiterUserId());
        }
        if (isChannelLeader(context)) {
            return record.getFinalChannelUserId() != null
                    && matchesDeptMember(record.getFinalChannelUserId(), context);
        }
        if (isRecruiterLeader(context)) {
            return record.getFinalRecruiterUserId() != null
                    && matchesDeptMember(record.getFinalRecruiterUserId(), context);
        }
        if (context.dataScope() == DataScope.PERSONAL) {
            return userId.equals(record.getFinalChannelUserId())
                    || userId.equals(record.getFinalRecruiterUserId());
        }
        return true;
    }

    public static void appendScopeCondition(
            StringBuilder where,
            List<Object> args,
            PerformanceAccessContext context,
            String prAlias) {
        if (where == null || context == null) {
            return;
        }
        String pr = StringUtils.hasText(prAlias) ? prAlias.trim() : "pr";
        if (isAdminLike(context)) {
            return;
        }
        UUID userId = context.userId();
        UUID deptId = context.deptId();
        if (isChannelStaffOnly(context)) {
            where.append(" AND ").append(pr).append(".final_channel_user_id = ?");
            args.add(requireScopeUser(userId));
            return;
        }
        if (isRecruiterStaffOnly(context)) {
            where.append(" AND ").append(pr).append(".final_recruiter_user_id = ?");
            args.add(requireScopeUser(userId));
            return;
        }
        if (isChannelLeader(context)) {
            where.append(" AND ").append(pr).append(".final_channel_user_id IN (")
                    .append(deptUserSubquery()).append(")");
            args.add(requireScopeDept(deptId));
            return;
        }
        if (isRecruiterLeader(context)) {
            where.append(" AND ").append(pr).append(".final_recruiter_user_id IN (")
                    .append(deptUserSubquery()).append(")");
            args.add(requireScopeDept(deptId));
            return;
        }
        if (context.dataScope() == DataScope.DEPT) {
            where.append(" AND (").append(pr).append(".final_channel_user_id IN (")
                    .append(deptUserSubquery()).append(")")
                    .append(" OR ").append(pr).append(".final_recruiter_user_id IN (")
                    .append(deptUserSubquery()).append("))");
            UUID requiredDeptId = requireScopeDept(deptId);
            args.add(requiredDeptId);
            args.add(requiredDeptId);
            return;
        }
        if (context.dataScope() == DataScope.PERSONAL) {
            where.append(" AND (").append(pr).append(".final_channel_user_id = ? OR ")
                    .append(pr).append(".final_recruiter_user_id = ?)");
            UUID requiredUserId = requireScopeUser(userId);
            args.add(requiredUserId);
            args.add(requiredUserId);
        }
    }

    private static UUID requireScopeUser(UUID userId) {
        if (userId == null) {
            throw BusinessException.forbidden("数据权限异常：缺少用户上下文");
        }
        return userId;
    }

    private static UUID requireScopeDept(UUID deptId) {
        if (deptId == null) {
            throw BusinessException.forbidden("数据权限异常：缺少部门上下文");
        }
        return deptId;
    }

    private static String deptUserSubquery() {
        return "SELECT id FROM sys_user WHERE dept_id = ? AND deleted = 0";
    }

    private static boolean isAdminLike(PerformanceAccessContext context) {
        return context.dataScope() == DataScope.ALL
                || hasAnyRole(context.roleCodes(), RoleCodes.ADMIN, RoleCodes.OPS_STAFF);
    }

    private static boolean isChannelStaffOnly(PerformanceAccessContext context) {
        List<String> roles = context.roleCodes();
        return hasAnyRole(roles, RoleCodes.CHANNEL_STAFF)
                && !hasAnyRole(roles, RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER, RoleCodes.OPS_STAFF);
    }

    private static boolean isRecruiterStaffOnly(PerformanceAccessContext context) {
        List<String> roles = context.roleCodes();
        return hasAnyRole(roles, RoleCodes.BIZ_STAFF)
                && !hasAnyRole(roles, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.COLONEL_LEADER, RoleCodes.OPS_STAFF);
    }

    private static boolean isChannelLeader(PerformanceAccessContext context) {
        return hasAnyRole(context.roleCodes(), RoleCodes.CHANNEL_LEADER)
                && !hasAnyRole(context.roleCodes(), RoleCodes.ADMIN);
    }

    private static boolean isRecruiterLeader(PerformanceAccessContext context) {
        return hasAnyRole(context.roleCodes(), RoleCodes.BIZ_LEADER, RoleCodes.COLONEL_LEADER)
                && !hasAnyRole(context.roleCodes(), RoleCodes.ADMIN);
    }

    private static boolean isChannelScoped(PerformanceAccessContext context) {
        return isChannelStaffOnly(context) || isChannelLeader(context);
    }

    private static boolean isRecruiterScoped(PerformanceAccessContext context) {
        return isRecruiterStaffOnly(context) || isRecruiterLeader(context);
    }

    private static void assertChannelFilter(UUID channelId, PerformanceAccessContext context) {
        if (isChannelStaffOnly(context) && !channelId.equals(context.userId())) {
            throw BusinessException.forbidden("无权按该渠道筛选业绩");
        }
    }

    private static void assertRecruiterFilter(UUID recruiterId, PerformanceAccessContext context) {
        if (isRecruiterStaffOnly(context) && !recruiterId.equals(context.userId())) {
            throw BusinessException.forbidden("无权按该招商筛选业绩");
        }
    }

    private static boolean matchesDeptMember(UUID targetUserId, PerformanceAccessContext context) {
        if (targetUserId == null || context.deptId() == null) {
            return false;
        }
        return targetUserId.equals(context.userId());
    }

    private static boolean hasAnyRole(List<String> roleCodes, String... expected) {
        if (roleCodes == null || roleCodes.isEmpty() || expected == null) {
            return false;
        }
        for (String role : roleCodes) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            String normalized = role.trim().toLowerCase(Locale.ROOT);
            for (String candidate : expected) {
                if (candidate != null && candidate.toLowerCase(Locale.ROOT).equals(normalized)) {
                    return true;
                }
            }
        }
        return false;
    }
}
