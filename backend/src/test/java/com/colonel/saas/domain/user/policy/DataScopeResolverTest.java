package com.colonel.saas.domain.user.policy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeResolverTest {

    private final DataScopePolicy policy = new DataScopePolicy();
    private final DataScopeResolver resolver = new DataScopeResolver(policy);

    @Test
    void resolve_personalWithUser_shouldExposeFilterUserDecision() {
        UUID userId = UUID.randomUUID();

        DataScopeResolver.ResolvedDataScope result =
                resolver.resolve(userId, null, DataScope.PERSONAL);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.decision()).isEqualTo(DataScopePolicy.Decision.FILTER_USER);
        assertThat(result.contextRequirement()).isEqualTo(DataScopePolicy.ContextRequirement.SATISFIED);
        assertThat(result.contextSatisfied()).isTrue();
        assertThat(result.filtersUser()).isTrue();
        assertThat(result.filtersDept()).isFalse();
        assertThat(result.noFilter()).isFalse();
    }

    @Test
    void resolve_personalWithoutUser_shouldExposeMissingUserContext() {
        DataScopeResolver.ResolvedDataScope result =
                resolver.resolve(null, UUID.randomUUID(), DataScope.PERSONAL);

        assertThat(result.decision()).isEqualTo(DataScopePolicy.Decision.NO_FILTER);
        assertThat(result.contextRequirement()).isEqualTo(DataScopePolicy.ContextRequirement.MISSING_USER);
        assertThat(result.contextSatisfied()).isFalse();
        assertThat(result.missingUser()).isTrue();
        assertThat(result.noFilter()).isTrue();
        assertThat(result.filtersUser()).isFalse();
        assertThat(result.filtersDept()).isFalse();
    }

    @Test
    void resolve_deptWithoutDept_shouldExposeMissingDeptContext() {
        DataScopeResolver.ResolvedDataScope result =
                resolver.resolve(UUID.randomUUID(), null, DataScope.DEPT);

        assertThat(result.decision()).isEqualTo(DataScopePolicy.Decision.NO_FILTER);
        assertThat(result.contextRequirement()).isEqualTo(DataScopePolicy.ContextRequirement.MISSING_DEPT);
        assertThat(result.contextSatisfied()).isFalse();
        assertThat(result.missingDept()).isTrue();
        assertThat(result.noFilter()).isTrue();
        assertThat(result.filtersUser()).isFalse();
        assertThat(result.filtersDept()).isFalse();
    }

    @Test
    void applyToQuery_shouldKeepPolicyWrapperBehavior() {
        UUID deptId = UUID.randomUUID();
        QueryWrapper<Object> actual = new QueryWrapper<>();
        QueryWrapper<Object> expected = new QueryWrapper<>();

        resolver.applyTo(actual, null, deptId, DataScope.DEPT, "user_id", "dept_id");
        policy.applyTo(expected, null, deptId, DataScope.DEPT, "user_id", "dept_id");

        assertThat(actual.getSqlSegment()).isEqualTo(expected.getSqlSegment());
        assertThat(actual.getParamNameValuePairs().values())
                .containsExactlyElementsOf(expected.getParamNameValuePairs().values());
    }

    @Test
    void buildFilter_shouldDelegateWithoutChangingOutput() {
        UUID userId = UUID.randomUUID();

        assertThat(resolver.buildFilter(userId, null, DataScope.PERSONAL, "owner_id", "dept_id"))
                .isEqualTo(policy.buildFilter(userId, null, DataScope.PERSONAL, "owner_id", "dept_id"));
    }
}
