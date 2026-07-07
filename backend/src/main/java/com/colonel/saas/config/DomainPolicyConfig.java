package com.colonel.saas.config;

import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.domain.sample.policy.SampleActionPermissionPolicy;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.OrgAssignmentPolicy;
import com.colonel.saas.domain.user.policy.OrgEnrichmentPolicy;
import com.colonel.saas.domain.user.policy.OrgValidationPolicy;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.policy.UserAssignmentPolicy;
import com.colonel.saas.domain.user.policy.UserChannelCodePolicy;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy;
import com.colonel.saas.domain.user.port.OrgDeletionConstraintLookup;
import com.colonel.saas.domain.user.port.OrgEnrichmentLookup;
import com.colonel.saas.domain.user.port.OrgLeaderCandidateLookup;
import com.colonel.saas.domain.user.port.OrgNodeLookup;
import com.colonel.saas.domain.user.port.UserAssignmentLookup;
import com.colonel.saas.domain.user.port.UserChannelCodeRegistry;
import com.colonel.saas.domain.user.port.UserDepartmentLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root for pure domain policies that still need Spring wiring at
 * application edges.
 */
@Configuration
public class DomainPolicyConfig {

    @Bean
    public ProductDisplayPolicy productDisplayPolicy() {
        return new ProductDisplayPolicy();
    }

    @Bean
    public CurrentUserPermissionPolicy currentUserPermissionPolicy() {
        return new CurrentUserPermissionPolicy();
    }

    @Bean
    public CurrentUserPermissionChecker currentUserPermissionChecker(
            CurrentUserPermissionPolicy currentUserPermissionPolicy) {
        return new CurrentUserPermissionChecker(currentUserPermissionPolicy);
    }

    @Bean
    public SampleActionPermissionPolicy sampleActionPermissionPolicy(
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        return new SampleActionPermissionPolicy(currentUserPermissionChecker);
    }

    @Bean
    public OrgAssignmentPolicy orgAssignmentPolicy(OrgNodeLookup orgNodeLookup) {
        return new OrgAssignmentPolicy(orgNodeLookup);
    }

    @Bean
    public OrgValidationPolicy orgValidationPolicy(
            OrgLeaderCandidateLookup leaderCandidateLookup,
            OrgDeletionConstraintLookup deletionConstraintLookup,
            CurrentUserPermissionPolicy currentUserPermissionPolicy) {
        return new OrgValidationPolicy(
                leaderCandidateLookup,
                deletionConstraintLookup,
                currentUserPermissionPolicy);
    }

    @Bean
    public OrgEnrichmentPolicy orgEnrichmentPolicy(
            OrgEnrichmentLookup orgEnrichmentLookup,
            OrgAssignmentPolicy orgAssignmentPolicy) {
        return new OrgEnrichmentPolicy(orgEnrichmentLookup, orgAssignmentPolicy);
    }

    @Bean
    public UserAccessPolicy userAccessPolicy(UserDepartmentLookup userDepartmentLookup) {
        return new UserAccessPolicy(userDepartmentLookup);
    }

    @Bean
    public UserAssignmentPolicy userAssignmentPolicy(UserAssignmentLookup userAssignmentLookup) {
        return new UserAssignmentPolicy(userAssignmentLookup);
    }

    @Bean
    public UserChannelCodePolicy userChannelCodePolicy(UserChannelCodeRegistry userChannelCodeRegistry) {
        return new UserChannelCodePolicy(userChannelCodeRegistry);
    }

    @Bean
    public UserCredentialPolicy userCredentialPolicy() {
        return new UserCredentialPolicy();
    }
}
