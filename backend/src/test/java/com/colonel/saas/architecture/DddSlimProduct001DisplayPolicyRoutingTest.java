package com.colonel.saas.architecture;

import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DddSlimProduct001DisplayPolicyRoutingTest {

    @Test
    void productDisplayPolicy_shouldOwnProductDisplayPresentationRules() {
        Set<String> policyMethods = Arrays.stream(ProductDisplayPolicy.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(policyMethods)
                .contains(
                        "resolveDisplayPresentation",
                        "resolveActivityProductStatusPresentation",
                        "legacyDisplayMark");
    }

    @Test
    void productService_shouldNotOwnDisplayPresentationDecisionHelpers() {
        Set<String> serviceMethods = Arrays.stream(ProductService.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(serviceMethods)
                .doesNotContain(
                        "resolveOfficialStatus",
                        "resolveReviewStatus",
                        "resolvePublishStatus",
                        "toLegacyDisplayMark");
    }
}
