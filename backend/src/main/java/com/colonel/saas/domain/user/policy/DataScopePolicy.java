package com.colonel.saas.domain.user.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.colonel.saas.common.enums.DataScope;

import java.util.UUID;

/**
 * 数据范围过滤条件策略（DDD-USER-DATASCOPE-001）。
 *
 * <p><b>职责</b>：根据数据范围（PERSONAL/DEPT/ALL）向查询 wrapper 追加等值条件。
 * 业务域（包括订单 Controller）通过本 Policy 应用过滤条件，无需自行判断
 * PERSONAL/DEPT/ALL。
 *
 * <p><b>领域边界</b>：本类位于用户域（domain/user/policy/）。
 * 业务域调用本 Policy 时只需要传入 userId/deptId/dataScope，无需了解过滤细节。
 *
 * <h3>过滤规则（行为 1:1 等价于 OrderController 旧实现）</h3>
 * <ul>
 *   <li>PERSONAL + userId 非空 → {@code "user_id = ?"}</li>
 *   <li>PERSONAL + userId 为空 → ""（防御性，避免越权返回空集）</li>
 *   <li>DEPT + deptId 非空 → {@code "dept_id = ?"}</li>
 *   <li>DEPT + deptId 为空 → ""</li>
 *   <li>ALL → ""（管理员）</li>
 * </ul>
 *
 * <h3>为什么放在用户域</h3>
 * <p>数据范围是<b>用户身份属性</b>的延伸，由用户域（角色 + 组织 + data_scope）派生。
 * 业务域不应自行解析数据范围 —— 只接收已解析的 userId/deptId/dataScope。</p>
 *
 * <h3>设计取舍</h3>
 * <p>本 Policy 保留 {@link #buildFilter(UUID, UUID, DataScope, String, String)}
 * 作为字符串级别的轻量断言入口；生产调用优先使用 {@code applyTo(...)}，
 * 直接复用 MyBatis-Plus 的 {@code eq} 参数绑定能力：
 * <ul>
 *   <li><b>可测试</b>：Policy 单测验证分支，等价性测试验证 wrapper 行为</li>
 *   <li><b>参数安全</b>：不拼接值，不使用 raw SQL 占位符</li>
 *   <li><b>类型安全</b>：LambdaQueryWrapper 路径由调用方传实体 getter</li>
 * </ul>
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String fragment = dataScopePolicy.buildFilter(userId, deptId, dataScope, "user_id", "dept_id");
 * if (!fragment.isEmpty()) {
 *     wrapper.apply(fragment);
 * }
 * }</pre>
 *
 * <h3>回滚方案</h3>
 * <p>如果发现问题，业务域旧实现保留，可直接 revert 调用点。
 * 本类为新增，不删除任何旧代码。</p>
 *
 * @see com.colonel.saas.common.enums.DataScope
 * @see com.colonel.saas.domain.user.facade.UserDomainFacade
 */
public class DataScopePolicy {

    public <T> void applyTo(
            LambdaQueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            SFunction<T, ?> userIdColumn,
            SFunction<T, ?> deptIdColumn) {

        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null && userIdColumn != null) {
                    wrapper.eq(userIdColumn, userId);
                }
            }
            case DEPT -> {
                if (deptId != null && deptIdColumn != null) {
                    wrapper.eq(deptIdColumn, deptId);
                }
            }
            case ALL -> {
                // no filter
            }
        }
    }

    public <T> void applyTo(
            QueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            String userIdColumn,
            String deptIdColumn) {

        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null && hasText(userIdColumn)) {
                    wrapper.eq(userIdColumn, userId);
                }
            }
            case DEPT -> {
                if (deptId != null && hasText(deptIdColumn)) {
                    wrapper.eq(deptIdColumn, deptId);
                }
            }
            case ALL -> {
                // no filter
            }
        }
    }

    public <T> void applyTo(
            QueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        applyTo(wrapper, userId, deptId, dataScope, "user_id", "dept_id");
    }

    /**
     * 数据范围过滤决策（DDD-USER-DATASCOPE-003 接入用）。
     *
     * <p>调用方根据 Decision 执行对应的 wrapper.eq 调用。
     * 之所以不让 Policy 直接修改 wrapper，是因为 MyBatis-Plus 的
     * {@code Wrapper.apply} 是 protected，外部类无法直接调用，
     * 只能通过 wrapper.eq 间接传入条件。</p>
     */
    public enum Decision {
        /** 添加 user_id = ? 条件 */
        FILTER_USER,
        /** 添加 dept_id = ? 条件 */
        FILTER_DEPT,
        /** 不添加过滤 */
        NO_FILTER
    }

    public enum ContextRequirement {
        SATISFIED,
        MISSING_USER,
        MISSING_DEPT
    }

    /**
     * 决策数据范围过滤类型（DDD-USER-DATASCOPE-003）。
     *
     * <p>调用方根据返回值决定调用 wrapper.eq 还是 no-op。
     * 行为 1:1 等价于 OrderController 旧 switch 实现。</p>
     *
     * @param userId    当前用户 ID（PERSONAL 范围使用）
     * @param deptId    当前用户部门 ID（DEPT 范围使用）
     * @param dataScope 数据范围枚举
     * @return 过滤决策（FILTER_USER / FILTER_DEPT / NO_FILTER）
     */
    public Decision decide(UUID userId, UUID deptId, DataScope dataScope) {
        if (dataScope == null) {
            return Decision.NO_FILTER;
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null) {
                    return Decision.FILTER_USER;
                }
                return Decision.NO_FILTER;
            }
            case DEPT -> {
                if (deptId != null) {
                    return Decision.FILTER_DEPT;
                }
                return Decision.NO_FILTER;
            }
            case ALL -> {
                return Decision.NO_FILTER;
            }
        }
        return Decision.NO_FILTER;
    }

    public boolean requiresFilter(DataScope dataScope) {
        return dataScope == DataScope.PERSONAL || dataScope == DataScope.DEPT;
    }

    public ContextRequirement contextRequirement(UUID userId, UUID deptId, DataScope dataScope) {
        if (dataScope == null) {
            return ContextRequirement.SATISFIED;
        }
        switch (dataScope) {
            case PERSONAL -> {
                return userId == null
                        ? ContextRequirement.MISSING_USER
                        : ContextRequirement.SATISFIED;
            }
            case DEPT -> {
                return deptId == null
                        ? ContextRequirement.MISSING_DEPT
                        : ContextRequirement.SATISFIED;
            }
            case ALL -> {
                return ContextRequirement.SATISFIED;
            }
        }
        return ContextRequirement.SATISFIED;
    }

    /**
     * 构建数据范围过滤 SQL 片段。
     *
     * @param userId        当前用户 ID（PERSONAL 范围使用）
     * @param deptId        当前用户部门 ID（DEPT 范围使用）
     * @param dataScope     数据范围枚举
     * @param userIdColumn  userId 列名（如 "user_id"）
     * @param deptIdColumn  deptId 列名（如 "dept_id"）
     * @return SQL WHERE 片段（含列名 = ? 占位符），无过滤时返回 ""
     */
    public String buildFilter(
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            String userIdColumn,
            String deptIdColumn) {

        if (dataScope == null) {
            return "";
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null && userIdColumn != null && !userIdColumn.isEmpty()) {
                    return userIdColumn + " = ?";
                }
                return "";
            }
            case DEPT -> {
                if (deptId != null && deptIdColumn != null && !deptIdColumn.isEmpty()) {
                    return deptIdColumn + " = ?";
                }
                return "";
            }
            case ALL -> {
                return "";
            }
        }
        return "";
    }

    /**
     * 便捷方法：使用默认列名 "user_id" 和 "dept_id"。
     *
     * <p>适用于<b>大多数</b>实体（订单、达人、业绩等都使用这两个列名）。
     * 特殊实体可调用 {@link #buildFilter(UUID, UUID, DataScope, String, String)}
     * 指定自定义列名。</p>
     *
     * @param userId    当前用户 ID
     * @param deptId    当前用户部门 ID
     * @param dataScope 数据范围枚举
     * @return SQL WHERE 片段，无过滤时返回 ""
     */
    public String buildFilter(UUID userId, UUID deptId, DataScope dataScope) {
        return buildFilter(userId, deptId, dataScope, "user_id", "dept_id");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
