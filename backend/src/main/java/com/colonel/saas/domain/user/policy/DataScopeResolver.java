package com.colonel.saas.domain.user.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.colonel.saas.common.enums.DataScope;

import java.util.Objects;
import java.util.UUID;

/**
 * User-domain resolver for data-scope decisions.
 *
 * <p>This class keeps the current {@link DataScopePolicy} behavior intact while
 * giving application services one stable dependency for resolving scope context.
 * Cross-domain migrations can move from direct policy calls to this resolver
 * without changing PERSONAL / DEPT / ALL semantics.</p>
 */
public class DataScopeResolver {

    private final DataScopePolicy dataScopePolicy;

    public DataScopeResolver(DataScopePolicy dataScopePolicy) {
        this.dataScopePolicy = Objects.requireNonNull(dataScopePolicy, "dataScopePolicy");
    }

    public ResolvedDataScope resolve(UUID userId, UUID deptId, DataScope dataScope) {
        return new ResolvedDataScope(
                userId,
                deptId,
                dataScope,
                dataScopePolicy.decide(userId, deptId, dataScope),
                dataScopePolicy.contextRequirement(userId, deptId, dataScope));
    }

    public boolean requiresFilter(DataScope dataScope) {
        return dataScopePolicy.requiresFilter(dataScope);
    }

    public <T> void applyTo(
            LambdaQueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            SFunction<T, ?> userIdColumn,
            SFunction<T, ?> deptIdColumn) {
        dataScopePolicy.applyTo(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
    }

    public <T> void applyTo(
            QueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            String userIdColumn,
            String deptIdColumn) {
        dataScopePolicy.applyTo(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
    }

    public String buildFilter(
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            String userIdColumn,
            String deptIdColumn) {
        return dataScopePolicy.buildFilter(userId, deptId, dataScope, userIdColumn, deptIdColumn);
    }

    public record ResolvedDataScope(
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            DataScopePolicy.Decision decision,
            DataScopePolicy.ContextRequirement contextRequirement) {

        public boolean contextSatisfied() {
            return contextRequirement == DataScopePolicy.ContextRequirement.SATISFIED;
        }

        public boolean missingUser() {
            return contextRequirement == DataScopePolicy.ContextRequirement.MISSING_USER;
        }

        public boolean missingDept() {
            return contextRequirement == DataScopePolicy.ContextRequirement.MISSING_DEPT;
        }

        public boolean noFilter() {
            return decision == DataScopePolicy.Decision.NO_FILTER;
        }

        public boolean filtersUser() {
            return decision == DataScopePolicy.Decision.FILTER_USER;
        }

        public boolean filtersDept() {
            return decision == DataScopePolicy.Decision.FILTER_DEPT;
        }
    }
}
