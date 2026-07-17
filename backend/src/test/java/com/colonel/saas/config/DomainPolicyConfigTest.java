package com.colonel.saas.config;

import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.domain.sample.policy.SampleActionPermissionPolicy;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainPolicyConfigTest {

    private final DomainPolicyConfig config = new DomainPolicyConfig();

    @Test
    void currentUserPermissionChecker_shouldUseTheConfiguredPermissionPolicy() {
        CurrentUserPermissionPolicy policy = config.currentUserPermissionPolicy();
        CurrentUserPermissionChecker checker = config.currentUserPermissionChecker(policy);

        assertThat(checker.normalizeRoleCodes("[ ADMIN , biz_staff , ADMIN ]"))
                .containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF);
        assertThat(checker.checkPermission(
                List.of(RoleCodes.ADMIN),
                List.of(),
                new CheckPermissionRequest("Product", "Delete")).allowed())
                .isTrue();
    }

    @Test
    void sampleActionPermissionPolicy_shouldKeepUsingTheSameUserPermissionPolicyRuleSource() {
        CurrentUserPermissionPolicy policy = config.currentUserPermissionPolicy();
        CurrentUserPermissionChecker checker = config.currentUserPermissionChecker(policy);
        SampleActionPermissionPolicy samplePolicy = config.sampleActionPermissionPolicy(checker);

        samplePolicy.ensureCanApply(List.of(RoleCodes.CHANNEL_STAFF));
        samplePolicy.ensureCanApply(List.of(RoleCodes.BIZ_STAFF));
        assertThatThrownBy(() -> samplePolicy.ensureCanApply(List.of(RoleCodes.OPS_STAFF)))
                .isInstanceOf(ForbiddenException.class);
    }
}
