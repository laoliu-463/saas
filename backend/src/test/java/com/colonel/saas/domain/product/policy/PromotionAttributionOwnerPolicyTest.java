package com.colonel.saas.domain.product.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionAttributionOwnerPolicyTest {

    private final PromotionAttributionOwnerPolicy policy = new PromotionAttributionOwnerPolicy();

    @Test
    void resolvesChannelRoles() {
        assertThat(policy.resolve(Set.of(RoleCodes.CHANNEL_STAFF)))
                .contains(AttributionOwnerType.CHANNEL);
        assertThat(policy.resolve(Set.of(RoleCodes.CHANNEL_LEADER)))
                .contains(AttributionOwnerType.CHANNEL);
    }

    @Test
    void resolvesRecruiterRoles() {
        assertThat(policy.resolve(Set.of(RoleCodes.BIZ_STAFF)))
                .contains(AttributionOwnerType.RECRUITER);
        assertThat(policy.resolve(Set.of(RoleCodes.BIZ_LEADER)))
                .contains(AttributionOwnerType.RECRUITER);
    }

    @Test
    void requiresExplicitSelectionForChannelAndRecruiterRoleConflict() {
        assertThatThrownBy(() -> policy.resolve(Set.of(
                RoleCodes.CHANNEL_STAFF,
                RoleCodes.BIZ_STAFF)))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode())
                            .isEqualTo("ATTRIBUTION_OWNER_TYPE_SELECTION_REQUIRED");
                    assertThat(error.getMessage()).contains("必须明确选择");
                });
        assertThat(policy.resolve(
                Set.of(RoleCodes.CHANNEL_STAFF, RoleCodes.BIZ_STAFF), AttributionOwnerType.RECRUITER))
                .contains(AttributionOwnerType.RECRUITER);
    }

    @Test
    void returnsEmptyForNonAttributionRole() {
        assertThat(policy.resolve(Set.of(RoleCodes.ADMIN))).isEmpty();
        assertThat(policy.resolve(Set.of())).isEmpty();
        assertThat(policy.resolve(null)).isEmpty();
    }
}
