package com.colonel.saas.architecture;

import com.colonel.saas.controller.ColonelActivityController;
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
                        "normalizeActivityProductStatus",
                        "normalizeActivityProductFilterStatus",
                        "normalizeActivityProductStatusText",
                        "isSupportedActivityProductQueryStatus",
                        "activityProductQueryStatusHint",
                        "normalizeActivityProductSortBy",
                        "normalizeSelectedLibrarySortBy",
                        "matchesSelectedLibraryPromotionLinkFilter",
                        "hasPromotionLink",
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
                        "normalizeActivityProductStatus",
                        "normalizeActivityProductFilterStatus",
                        "normalizeActivityProductStatusText",
                        "normalizeActivityProductSortBy",
                        "normalizeSelectedLibrarySortBy",
                        "matchesPromotionLinkFilter",
                        "hasPromotionLink",
                        "hasActivityPromotionLink",
                        "toLegacyDisplayMark");
    }

    @Test
    void selectedLibraryFilter_shouldNotOwnSortNormalization() {
        Set<String> filterMethods = Arrays.stream(ProductService.SelectedLibraryFilter.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(filterMethods)
                .doesNotContain("normalizeSortBy");
    }

    @Test
    void colonelActivityController_shouldDelegateActivityProductStatusEnumToProductPolicy() {
        Set<String> controllerMethods = Arrays.stream(ColonelActivityController.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(controllerMethods)
                .doesNotContain("validateActivityProductStatus");
    }
}
