package com.colonel.saas.domain.performance.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.entity.PerformanceRecord;

import java.util.List;
import java.util.UUID;

/**
 * 业绩查询数据范围过滤器，按角色和部门限制用户可查看的业绩记录。
 *
 * <ul>
 *   <li>ADMIN / OPS_STAFF：可查看全部业绩（ALL 数据范围）</li>
 *   <li>CHANNEL_STAFF：仅可查看自己作为 final_channel_user 的业绩</li>
 *   <li>CHANNEL_LEADER：可查看本部门下所有渠道人员的业绩</li>
 *   <li>BIZ_STAFF：仅可查看自己作为 final_recruiter_user 的业绩</li>
 *   <li>BIZ_LEADER：可查看本部门下所有招商人员的业绩</li>
 *   <li>PERSONAL 数据范围：当前用户既是渠道又是招商时，可查看两个维度中自己的记录</li>
 * </ul>
 *
 * <p>在架构中属于业绩域（Performance Domain）的权限控制工具类，
 * 为 {@link PerformanceQueryService} 和 {@link PerformanceMetricsQueryService} 提供 SQL 级数据隔离。</p>
 *
 * @see PerformanceAccessContext
 * @see PerformanceQueryService
 */
public final class PerformanceAccessScope {

    /** 工具类，禁止实例化 */
    private PerformanceAccessScope() {
    }

    /**
     * 判断当前用户是否有业绩导出权限。
     *
     * <ol>
     *   <li>ADMIN 角色：可导出</li>
     *   <li>BIZ_LEADER / CHANNEL_LEADER：可导出本部门数据</li>
     *   <li>其他角色：不可导出</li>
     * </ol>
     *
     * @param context 访问上下文
     * @return true 表示可导出
     */
    public static boolean canExport(
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        if (context == null) {
            return false;
        }
        List<String> roles = context.roleCodes();
        return checkerHasAnyRole(currentUserPermissionChecker, roles, RoleCodes.ADMIN)
                || checkerHasAnyRole(currentUserPermissionChecker, roles, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER);
    }

    /**
     * 判断当前用户是否有月度业绩重算权限（仅限 ADMIN）。
     *
     * @param context 访问上下文
     * @return true 表示可重算
     */
    public static boolean canRecalculateMonth(
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        return context != null && checkerHasAnyRole(currentUserPermissionChecker, context.roleCodes(), RoleCodes.ADMIN);
    }

    /**
     * 校验筛选参数是否越权：普通人员只能按自己的 ID 筛选。
     *
     * <ol>
     *   <li>第一步：若 context 为 null，直接抛出无权异常</li>
     *   <li>第二步：ADMIN 类角色跳过校验（拥有全部权限）</li>
     *   <li>第三步：渠道维度角色（CHANNEL_STAFF / CHANNEL_LEADER）校验 channelId 是否匹配</li>
     *   <li>第四步：招商维度角色（BIZ_STAFF / BIZ_LEADER）校验 recruiterId 是否匹配</li>
     * </ol>
     *
     * @param channelId   筛选的渠道用户 ID，可为 null
     * @param recruiterId 筛选的招商用户 ID，可为 null
     * @param context     访问上下文
     * @throws BusinessException 越权时抛出 403 异常
     */
    public static void assertFilterAllowed(
            UUID channelId,
            UUID recruiterId,
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        if (context == null) {
            throw BusinessException.forbidden("无权访问业绩数据");
        }
        // 第二步：ADMIN 类角色无需校验
        if (isAdminLike(context, currentUserPermissionChecker)) {
            return;
        }
        // 第三步：渠道维度角色校验
        if (channelId != null && isChannelScoped(context, currentUserPermissionChecker)) {
            assertChannelFilter(channelId, context, currentUserPermissionChecker);
        }
        // 第四步：招商维度角色校验
        if (recruiterId != null && isRecruiterScoped(context, currentUserPermissionChecker)) {
            assertRecruiterFilter(recruiterId, context, currentUserPermissionChecker);
        }
    }

    /**
     * 判断当前用户是否有权访问指定业绩记录（逐条校验）。
     *
     * <ol>
     *   <li>ADMIN / OPS_STAFF：直接放行</li>
     *   <li>CHANNEL_STAFF：仅当记录的 finalChannelUserId 等于当前用户</li>
     *   <li>BIZ_STAFF：仅当记录的 finalRecruiterUserId 等于当前用户</li>
     *   <li>CHANNEL_LEADER：记录的渠道归属人属于本部门</li>
     *   <li>BIZ_LEADER：记录的招商归属人属于本部门</li>
     *   <li>PERSONAL 数据范围：渠道或招商归属人任一等于当前用户即放行</li>
     * </ol>
     *
     * @param record  业绩记录
     * @param context 访问上下文
     * @return true 表示有权访问
     */
    public static boolean canAccessRecord(
            PerformanceRecord record,
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        if (record == null || context == null) {
            return false;
        }
        if (isAdminLike(context, currentUserPermissionChecker)) {
            return true;
        }
        UUID userId = context.userId();
        if (userId == null) {
            return false;
        }
        // 渠道人员：仅查看自己作为渠道归属人的记录
        if (isChannelStaffOnly(context, currentUserPermissionChecker)) {
            return userId.equals(record.getFinalChannelUserId());
        }
        // 招商人员：仅查看自己作为招商归属人的记录
        if (isRecruiterStaffOnly(context, currentUserPermissionChecker)) {
            return userId.equals(record.getFinalRecruiterUserId());
        }
        // 渠道组长：查看本部门渠道人员的记录
        if (isChannelLeader(context, currentUserPermissionChecker)) {
            return record.getFinalChannelUserId() != null
                    && matchesDeptMember(record.getFinalChannelUserId(), context);
        }
        // 招商组长：查看本部门招商人员的记录
        if (isRecruiterLeader(context, currentUserPermissionChecker)) {
            return record.getFinalRecruiterUserId() != null
                    && matchesDeptMember(record.getFinalRecruiterUserId(), context);
        }
        // PERSONAL 数据范围：渠道或招商归属人任一匹配即放行
        if (context.dataScope() == DataScope.PERSONAL) {
            return userId.equals(record.getFinalChannelUserId())
                    || userId.equals(record.getFinalRecruiterUserId());
        }
        return true;
    }

    /**
     * 向 SQL WHERE 子句追加数据范围条件，实现 SQL 级数据隔离。
     *
     * <ol>
     *   <li>ADMIN / OPS_STAFF：不追加条件（可查看全部）</li>
     *   <li>CHANNEL_STAFF：追加 final_channel_user_id = ? 条件</li>
     *   <li>BIZ_STAFF：追加 final_recruiter_user_id = ? 条件</li>
     *   <li>CHANNEL_LEADER：追加 final_channel_user_id IN (部门子查询) 条件</li>
     *   <li>BIZ_LEADER：追加 final_recruiter_user_id IN (部门子查询) 条件</li>
     *   <li>DEPT 数据范围：渠道或招商归属人任一属于本部门即放行（OR 条件）</li>
     *   <li>PERSONAL 数据范围：渠道或招商归属人任一等于当前用户（OR 条件）</li>
     * </ol>
     *
     * @param where    SQL WHERE 子句 StringBuilder，追加 AND 条件
     * @param args     参数化查询的参数列表，追加 ? 对应的值
     * @param context  访问上下文
     * @param prAlias  业绩记录表的 SQL 别名，默认 "pr"
     */
    public static void appendScopeCondition(
            StringBuilder where,
            List<Object> args,
            PerformanceAccessContext context,
            String prAlias,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        if (where == null || context == null) {
            return;
        }
        String pr = hasText(prAlias) ? prAlias.trim() : "pr";
        if (appendRoleScopeCondition(where, args, context, pr, currentUserPermissionChecker)) {
            return;
        }
        appendLegacyDataScopeFallback(where, args, context, pr);
    }

    /**
     * 向 SQL WHERE 子句追加数据范围条件，数据范围解释委托给用户域 {@link DataScopeResolver}。
     *
     * <p>该方法是 DDD 数据范围旁路，供调用方在灰度开关开启后使用。
     * 角色维度仍由业绩域策略解释，只有 PERSONAL / DEPT / ALL 数据范围决策交给用户域。</p>
     */
    public static void appendScopeConditionWithResolver(
            StringBuilder where,
            List<Object> args,
            PerformanceAccessContext context,
            String prAlias,
            DataScopeResolver dataScopeResolver,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        if (dataScopeResolver == null) {
            appendScopeCondition(where, args, context, prAlias, currentUserPermissionChecker);
            return;
        }
        if (where == null || context == null) {
            return;
        }
        String pr = hasText(prAlias) ? prAlias.trim() : "pr";
        if (appendRoleScopeCondition(where, args, context, pr, currentUserPermissionChecker)) {
            return;
        }
        appendResolverDataScopeFallback(where, args, context, pr, dataScopeResolver);
    }

    private static boolean appendRoleScopeCondition(
            StringBuilder where,
            List<Object> args,
            PerformanceAccessContext context,
            String pr,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        if (isAdminLike(context, currentUserPermissionChecker)) {
            return true;
        }
        UUID userId = context.userId();
        UUID deptId = context.deptId();
        if (isChannelStaffOnly(context, currentUserPermissionChecker)) {
            where.append(" AND ").append(pr).append(".final_channel_user_id = ?");
            args.add(requireScopeUser(userId));
            return true;
        }
        if (isRecruiterStaffOnly(context, currentUserPermissionChecker)) {
            where.append(" AND ").append(pr).append(".final_recruiter_user_id = ?");
            args.add(requireScopeUser(userId));
            return true;
        }
        if (isChannelLeader(context, currentUserPermissionChecker)) {
            where.append(" AND ").append(pr).append(".final_channel_user_id IN (")
                    .append(deptUserSubquery()).append(")");
            args.add(requireScopeDept(deptId));
            return true;
        }
        if (isRecruiterLeader(context, currentUserPermissionChecker)) {
            where.append(" AND ").append(pr).append(".final_recruiter_user_id IN (")
                    .append(deptUserSubquery()).append(")");
            args.add(requireScopeDept(deptId));
            return true;
        }
        return false;
    }

    private static void appendLegacyDataScopeFallback(
            StringBuilder where,
            List<Object> args,
            PerformanceAccessContext context,
            String pr) {
        UUID userId = context.userId();
        UUID deptId = context.deptId();
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

    private static void appendResolverDataScopeFallback(
            StringBuilder where,
            List<Object> args,
            PerformanceAccessContext context,
            String pr,
            DataScopeResolver dataScopeResolver) {
        UUID userId = context.userId();
        UUID deptId = context.deptId();
        DataScopeResolver.ResolvedDataScope resolved =
                dataScopeResolver.resolve(userId, deptId, context.dataScope());
        if (resolved.missingUser()) {
            requireScopeUser(userId);
            return;
        }
        if (resolved.missingDept()) {
            requireScopeDept(deptId);
            return;
        }
        if (resolved.filtersDept()) {
            where.append(" AND (").append(pr).append(".final_channel_user_id IN (")
                    .append(deptUserSubquery()).append(")")
                    .append(" OR ").append(pr).append(".final_recruiter_user_id IN (")
                    .append(deptUserSubquery()).append("))");
            args.add(deptId);
            args.add(deptId);
            return;
        }
        if (resolved.filtersUser()) {
            where.append(" AND (").append(pr).append(".final_channel_user_id = ? OR ")
                    .append(pr).append(".final_recruiter_user_id = ?)");
            args.add(userId);
            args.add(userId);
        }
    }

    /**
     * 要求 userId 非空，否则抛出数据权限异常。
     *
     * @param userId 用户 ID
     * @return 验证通过的用户 ID
     * @throws BusinessException userId 为 null 时抛出 403 异常
     */
    private static UUID requireScopeUser(UUID userId) {
        if (userId == null) {
            throw BusinessException.forbidden("数据权限异常：缺少用户上下文");
        }
        return userId;
    }

    /**
     * 要求 deptId 非空，否则抛出数据权限异常。
     *
     * @param deptId 部门 ID
     * @return 验证通过的部门 ID
     * @throws BusinessException deptId 为 null 时抛出 403 异常
     */
    private static UUID requireScopeDept(UUID deptId) {
        if (deptId == null) {
            throw BusinessException.forbidden("数据权限异常：缺少部门上下文");
        }
        return deptId;
    }

    /**
     * 返回部门级用户子查询 SQL，用于 IN 条件筛选本部门成员。
     *
     * @return SQL 子查询字符串
     */
    private static String deptUserSubquery() {
        return "SELECT id FROM sys_user WHERE dept_id = ? AND deleted = 0";
    }

    /**
     * 判断是否为"管理员类"角色（数据范围 ALL 或 ADMIN / OPS_STAFF 角色）。
     *
     * @param context 访问上下文
     * @return true 表示管理员类角色，可查看全部数据
     */
    private static boolean isAdminLike(
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        return context.dataScope() == DataScope.ALL
                || checkerHasAnyRole(currentUserPermissionChecker, context.roleCodes(), RoleCodes.ADMIN, RoleCodes.OPS_STAFF);
    }

    /**
     * 判断是否为"纯渠道人员"角色（CHANNEL_STAFF 且不是 ADMIN / CHANNEL_LEADER / OPS_STAFF）。
     *
     * @param context 访问上下文
     * @return true 表示纯渠道人员，仅查看自己的渠道维度业绩
     */
    private static boolean isChannelStaffOnly(
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        List<String> roles = context.roleCodes();
        return checkerHasAnyRole(currentUserPermissionChecker, roles, RoleCodes.CHANNEL_STAFF)
                && !checkerHasAnyRole(currentUserPermissionChecker, roles, RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER, RoleCodes.OPS_STAFF);
    }

    /**
     * 判断是否为"纯招商人员"角色（BIZ_STAFF 且不是 ADMIN / BIZ_LEADER / OPS_STAFF）。
     *
     * @param context 访问上下文
     * @return true 表示纯招商人员，仅查看自己的招商维度业绩
     */
    private static boolean isRecruiterStaffOnly(
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        List<String> roles = context.roleCodes();
        return checkerHasAnyRole(currentUserPermissionChecker, roles, RoleCodes.BIZ_STAFF)
                && !checkerHasAnyRole(currentUserPermissionChecker, roles, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.OPS_STAFF);
    }

    /**
     * 判断是否为"渠道组长"角色（CHANNEL_LEADER 且不是 ADMIN）。
     *
     * @param context 访问上下文
     * @return true 表示渠道组长，可查看本部门渠道人员业绩
     */
    private static boolean isChannelLeader(
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        return checkerHasAnyRole(currentUserPermissionChecker, context.roleCodes(), RoleCodes.CHANNEL_LEADER)
                && !checkerHasAnyRole(currentUserPermissionChecker, context.roleCodes(), RoleCodes.ADMIN);
    }

    /**
     * 判断是否为"招商组长"角色（BIZ_LEADER 且不是 ADMIN）。
     *
     * @param context 访问上下文
     * @return true 表示招商组长，可查看本部门招商人员业绩
     */
    private static boolean isRecruiterLeader(
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        return checkerHasAnyRole(currentUserPermissionChecker, context.roleCodes(), RoleCodes.BIZ_LEADER)
                && !checkerHasAnyRole(currentUserPermissionChecker, context.roleCodes(), RoleCodes.ADMIN);
    }

    /**
     * 判断是否为渠道维度角色（渠道人员或渠道组长）。
     *
     * @param context 访问上下文
     * @return true 表示渠道维度角色
     */
    private static boolean isChannelScoped(
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        return isChannelStaffOnly(context, currentUserPermissionChecker)
                || isChannelLeader(context, currentUserPermissionChecker);
    }

    /**
     * 判断是否为招商维度角色（招商人员或招商组长）。
     *
     * @param context 访问上下文
     * @return true 表示招商维度角色
     */
    private static boolean isRecruiterScoped(
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        return isRecruiterStaffOnly(context, currentUserPermissionChecker)
                || isRecruiterLeader(context, currentUserPermissionChecker);
    }

    /**
     * 校验渠道维度筛选是否越权（纯渠道人员只能按自己的 ID 筛选）。
     *
     * @param channelId 筛选的渠道用户 ID
     * @param context   访问上下文
     * @throws BusinessException 越权时抛出 403 异常
     */
    private static void assertChannelFilter(
            UUID channelId,
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        if (isChannelStaffOnly(context, currentUserPermissionChecker) && !channelId.equals(context.userId())) {
            throw BusinessException.forbidden("无权按该渠道筛选业绩");
        }
    }

    /**
     * 校验招商维度筛选是否越权（纯招商人员只能按自己的 ID 筛选）。
     *
     * @param recruiterId 筛选的招商用户 ID
     * @param context     访问上下文
     * @throws BusinessException 越权时抛出 403 异常
     */
    private static void assertRecruiterFilter(
            UUID recruiterId,
            PerformanceAccessContext context,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        if (isRecruiterStaffOnly(context, currentUserPermissionChecker) && !recruiterId.equals(context.userId())) {
            throw BusinessException.forbidden("无权按该招商筛选业绩");
        }
    }

    /**
     * 判断目标用户是否与当前用户匹配（简化版部门成员校验）。
     *
     * <p>当前实现仅做 userId 精确匹配，后续可扩展为真正的部门成员子查询校验。</p>
     *
     * @param targetUserId 目标用户 ID
     * @param context      访问上下文
     * @return true 表示目标用户属于当前用户的管辖范围
     */
    private static boolean matchesDeptMember(UUID targetUserId, PerformanceAccessContext context) {
        if (targetUserId == null || context.deptId() == null) {
            return false;
        }
        return targetUserId.equals(context.userId());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean checkerHasAnyRole(
            CurrentUserPermissionChecker currentUserPermissionChecker,
            Object roleCodes,
            String... expectedRoles) {
        return currentUserPermissionChecker != null
                && currentUserPermissionChecker.hasAnyRole(roleCodes, expectedRoles);
    }
}
