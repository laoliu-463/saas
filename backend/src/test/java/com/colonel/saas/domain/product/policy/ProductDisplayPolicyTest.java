package com.colonel.saas.domain.product.policy;

import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.common.enums.ProductBizStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ProductDisplayPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 10, 12, 0);
    private static final String PRODUCT_ID = "P-9001";

    private ProductDisplayPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ProductDisplayPolicy();
    }

    @Test
    void zeroEligibleRelations_shouldHideAll() {
        ProductDisplayRelationInput expired = relation(
                "A1", 1000L, false, NOW.minusDays(5), NOW.minusDays(1));

        ProductDisplayPolicyResult result = policy.decide(List.of(expired), NOW);

        assertThat(result.selectedRelationId()).isNull();
        assertThat(result.displayDecision()).isEqualTo(ProductDisplayPolicy.DECISION_HIDE_ALL);
        assertThat(result.relationOutcomes()).allMatch(outcome ->
                ProductDisplayStatus.HIDDEN.name().equals(outcome.nextDisplayStatus()));
    }

    @Test
    void singleEligibleRelation_shouldDisplayIt() {
        ProductDisplayRelationInput only = eligible("A1", 2000L, false, NOW.minusDays(1));

        ProductDisplayPolicyResult result = policy.decide(List.of(only), NOW);

        assertThat(result.selectedRelationId()).isEqualTo(only.relationId());
        assertThat(result.displayDecision()).isEqualTo(ProductDisplayPolicy.DECISION_DISPLAY);
        assertThat(result.displayReasons().get(only.relationId()))
                .isEqualTo(ProductDisplayPolicy.DISPLAY_REASON_RULE);
    }

    @Test
    void multipleEligible_shouldPreferTrafficSupport() {
        ProductDisplayRelationInput lowCommissionAds = eligible("A1", 1000L, true, NOW.minusDays(3));
        ProductDisplayRelationInput highCommission = eligible("A2", 3000L, false, NOW.minusDays(1));

        ProductDisplayPolicyResult result = policy.decide(List.of(lowCommissionAds, highCommission), NOW);

        assertThat(result.selectedRelationId()).isEqualTo(lowCommissionAds.relationId());
    }

    @Test
    void multipleEligible_shouldPreferHigherCommissionWhenTrafficEqual() {
        ProductDisplayRelationInput lower = eligible("A1", 1000L, false, NOW.minusDays(1));
        ProductDisplayRelationInput higher = eligible("A2", 2800L, false, NOW);

        ProductDisplayPolicyResult result = policy.decide(List.of(lower, higher), NOW);

        assertThat(result.selectedRelationId()).isEqualTo(higher.relationId());
    }

    @Test
    void multipleEligible_shouldPreferLowerServiceFeeWhenCommissionEqual() {
        ProductDisplayRelationInput higherFee = eligibleWithServiceFee("A1", 2000L, false, "5", NOW.minusDays(1));
        ProductDisplayRelationInput lowerFee = eligibleWithServiceFee("A2", 2000L, false, "3", NOW);

        ProductDisplayPolicyResult result = policy.decide(List.of(higherFee, lowerFee), NOW);

        assertThat(result.selectedRelationId()).isEqualTo(lowerFee.relationId());
    }

    @Test
    void multipleEligible_shouldPreferLaterShelfTimeWhenRatesEqual() {
        ProductDisplayRelationInput earlier = eligible("A1", 2000L, false, NOW.minusDays(5));
        ProductDisplayRelationInput later = eligible("A2", 2000L, false, NOW.minusDays(1));

        ProductDisplayPolicyResult result = policy.decide(List.of(earlier, later), NOW);

        assertThat(result.selectedRelationId()).isEqualTo(later.relationId());
    }

    @Test
    void protectionPeriod_shouldNotSwitchWithoutAdvantage() {
        ProductDisplayRelationInput displaying = displaying(
                "A1", 2000L, false, NOW.minusMonths(2), NOW.minusMonths(2));
        ProductDisplayRelationInput challenger = eligible("A2", 2000L, false, NOW.minusDays(1));

        ProductDisplayPolicyResult result = policy.decide(List.of(displaying, challenger), NOW);

        assertThat(result.selectedRelationId()).isEqualTo(displaying.relationId());
        assertThat(result.hideReasons().get(challenger.relationId()))
                .isEqualTo(ProductDisplayPolicy.HIDDEN_REASON_REPLACED);
    }

    @Test
    void protectionPeriod_shouldSwitchOnAdvantageOverride() {
        ProductDisplayRelationInput displaying = displaying(
                "A1", 1500L, false, NOW.minusMonths(2), NOW.minusMonths(2));
        ProductDisplayRelationInput challenger = eligible("A2", 3000L, false, NOW.minusDays(1));

        ProductDisplayPolicyResult result = policy.decide(List.of(displaying, challenger), NOW);

        assertThat(result.selectedRelationId()).isEqualTo(challenger.relationId());
        assertThat(result.displayReasons().get(challenger.relationId()))
                .isEqualTo(ProductDisplayPolicy.DISPLAY_REASON_ADVANTAGE);
        assertThat(result.hideReasons().get(displaying.relationId()))
                .isEqualTo(ProductDisplayPolicy.HIDDEN_REASON_REPLACED_BY_ADVANTAGE);
        assertThat(result.whetherNeedEvent()).isTrue();
    }

    @Test
    void activityExpired_shouldHideDisplayingRelation() {
        ProductDisplayRelationInput eligible = eligible("A1", 2000L, false, NOW.minusDays(1));
        ProductDisplayRelationInput expired = relation(
                "A2", 4000L, true, NOW.minusDays(2), NOW.minusDays(1));

        ProductDisplayPolicyResult result = policy.decide(List.of(eligible, expired), NOW);

        assertThat(result.selectedRelationId()).isEqualTo(eligible.relationId());
        assertThat(result.hideReasons().get(expired.relationId()))
                .isEqualTo(ProductDisplayPolicy.HIDDEN_REASON_ACTIVITY_EXPIRED);
    }

    @Test
    void activityExtension_shouldReEvaluateAfterRecovery() {
        UUID relationId = UUID.randomUUID();
        ProductDisplayRelationInput previouslyExpired = new ProductDisplayRelationInput(
                relationId,
                PRODUCT_ID,
                "A1",
                "APPROVED",
                1,
                NOW.minusMonths(1),
                NOW.plusDays(7),
                ProductDisplayStatus.HIDDEN.name(),
                NOW.minusDays(10),
                NOW.minusMonths(2),
                NOW.minusMonths(2),
                BigDecimal.valueOf(4000),
                BigDecimal.ZERO,
                true,
                false,
                null,
                null,
                null,
                true,
                false,
                false,
                false,
                null,
                NOW.minusDays(10),
                3);
        ProductDisplayRelationInput challenger = eligible("A2", 2000L, false, NOW.minusDays(1));

        ProductDisplayPolicyResult result = policy.decide(List.of(previouslyExpired, challenger), NOW);

        assertThat(result.selectedRelationId()).isEqualTo(previouslyExpired.relationId());
        assertThat(result.displayReasons().get(previouslyExpired.relationId()))
                .isEqualTo(ProductDisplayPolicy.DISPLAY_REASON_RULE);
    }

    @Test
    void statusFourPresentation_shouldMapToTerminatedByUpstreamCode() {
        ProductDisplayPolicy.ActivityProductStatusPresentation presentation =
                policy.resolveActivityProductStatusPresentation(
                        4,
                        "合作前取消",
                        null,
                        null,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null);

        assertThat(presentation.officialStatus()).isEqualTo("TERMINATED");

        ProductDisplayPolicy.ActivityProductStatusPresentation textOnlyPresentation =
                policy.resolveActivityProductStatusPresentation(
                        null,
                        "合作前取消",
                        null,
                        null,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null);

        assertThat(textOnlyPresentation.officialStatus()).isNotEqualTo("TERMINATED");
    }

    @Test
    void unsupportedStatusFour_shouldNotNormalizeToTerminatedStatusForStorageAndFiltering() {
        assertThat(policy.normalizeActivityProductStatus(4)).isEqualTo(4);
        assertThat(policy.normalizeActivityProductStatus(Integer.valueOf(4))).isEqualTo(4);
        assertThat(policy.normalizeActivityProductFilterStatus(4)).isEqualTo(4);
        assertThat(policy.normalizeActivityProductStatusText(4, "合作前取消")).isEqualTo("合作前取消");
    }

    @Test
    void activityProductQueryStatusValidation_shouldKeepPublicEnumContract() {
        assertThat(policy.isSupportedActivityProductQueryStatus(null)).isTrue();
        assertThat(policy.isSupportedActivityProductQueryStatus(0)).isTrue();
        assertThat(policy.isSupportedActivityProductQueryStatus(1)).isTrue();
        assertThat(policy.isSupportedActivityProductQueryStatus(2)).isTrue();
        assertThat(policy.isSupportedActivityProductQueryStatus(3)).isTrue();
        assertThat(policy.isSupportedActivityProductQueryStatus(6)).isTrue();

        assertThat(policy.isSupportedActivityProductQueryStatus(4)).isFalse();
        assertThat(policy.isSupportedActivityProductQueryStatus(9)).isFalse();
        assertThat(policy.activityProductQueryStatusHint())
                .isEqualTo("商品状态仅支持 0=待审核、1=推广中、2=申请未通过、3=合作已终止、6=合作已到期");
    }

    @Test
    void activityProductSortBy_shouldKeepLegacyQueryBranchContract() {
        assertThat(policy.normalizeActivityProductSortBy(null)).isEqualTo("default");
        assertThat(policy.normalizeActivityProductSortBy("")).isEqualTo("default");
        assertThat(policy.normalizeActivityProductSortBy(" latest ")).isEqualTo("latest");
        assertThat(policy.normalizeActivityProductSortBy("LATEST")).isEqualTo("latest");
        assertThat(policy.normalizeActivityProductSortBy("pinned")).isEqualTo("default");
        assertThat(policy.normalizeActivityProductSortBy("unknown")).isEqualTo("default");
    }

    @Test
    void selectedLibrarySortBy_shouldKeepLegacyQueryBranchContract() {
        assertThat(policy.normalizeSelectedLibrarySortBy(null)).isEqualTo("default");
        assertThat(policy.normalizeSelectedLibrarySortBy("")).isEqualTo("default");
        assertThat(policy.normalizeSelectedLibrarySortBy(" default ")).isEqualTo("default");
        assertThat(policy.normalizeSelectedLibrarySortBy("pinned")).isEqualTo("default");
        assertThat(policy.normalizeSelectedLibrarySortBy(" latest ")).isEqualTo("latest");
        assertThat(policy.normalizeSelectedLibrarySortBy(" LATEST ")).isEqualTo("LATEST");
        assertThat(policy.normalizeSelectedLibrarySortBy("unknown")).isEqualTo("unknown");
    }

    @Test
    void promotionLinkPresence_shouldTreatAnyNonBlankLinkAsPromoted() {
        assertThat(policy.hasPromotionLink(null, "", "   ")).isFalse();
        assertThat(policy.hasPromotionLink("https://promote.example", null, null)).isTrue();
        assertThat(policy.hasPromotionLink(null, "https://short.example", null)).isTrue();
        assertThat(policy.hasPromotionLink(null, null, "https://latest.example")).isTrue();
    }

    @Test
    void selectedLibraryPromotionLinkFilter_shouldKeepLegacyStatusContract() {
        assertThat(policy.matchesSelectedLibraryPromotionLinkFilter(
                "LINKED", "https://promote.example", null, "APPROVED")).isTrue();
        assertThat(policy.matchesSelectedLibraryPromotionLinkFilter(
                "LINKED", null, null, "APPROVED")).isFalse();

        assertThat(policy.matchesSelectedLibraryPromotionLinkFilter(
                "PENDING", null, null, "APPROVED")).isTrue();
        assertThat(policy.matchesSelectedLibraryPromotionLinkFilter(
                "PENDING", null, null, "LINKED_FAILED")).isFalse();

        assertThat(policy.matchesSelectedLibraryPromotionLinkFilter(
                "FAILED", null, null, "FOLLOWING_FAILED")).isTrue();
        assertThat(policy.matchesSelectedLibraryPromotionLinkFilter(
                "unknown", null, null, "FOLLOWING_FAILED")).isTrue();
    }

    @Test
    void selectedLibraryPublishedAndListedFilters_shouldKeepLegacyContracts() {
        assertThat(policy.matchesSelectedLibraryPublishedFilter("1", "https://promote.example", null)).isTrue();
        assertThat(policy.matchesSelectedLibraryPublishedFilter("1", null, null)).isFalse();
        assertThat(policy.matchesSelectedLibraryPublishedFilter("0", null, "   ")).isTrue();
        assertThat(policy.matchesSelectedLibraryPublishedFilter("0", null, "https://short.example")).isFalse();

        assertThat(policy.matchesSelectedLibraryListedFilter("1", 1)).isTrue();
        assertThat(policy.matchesSelectedLibraryListedFilter("1", 0)).isFalse();
        assertThat(policy.matchesSelectedLibraryListedFilter("0", 0)).isTrue();
        assertThat(policy.matchesSelectedLibraryListedFilter("0", 1)).isFalse();
        assertThat(policy.matchesSelectedLibraryListedFilter("unexpected", 1)).isFalse();
    }

    @Test
    void selectedLibraryAllianceStatusFilter_shouldUseSupportedActivityStatusContract() {
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("pending_audit", 0, null)).isTrue();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("promoting", 1, null)).isTrue();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("rejected", 2, null)).isTrue();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("terminated", 3, null)).isTrue();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("terminated", 4, null)).isFalse();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("expired", 6, null)).isTrue();

        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("pending_audit", null, "审核中")).isTrue();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("promoting", null, "推广中")).isTrue();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("rejected", null, "申请未通过")).isTrue();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("terminated", null, "合作已终止")).isTrue();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("terminated", null, "合作前取消")).isFalse();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("expired", null, "已到期")).isTrue();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("unknown", null, "已到期")).isTrue();
        assertThat(policy.matchesSelectedLibraryAllianceStatusFilter("expired", 1, "推广中")).isFalse();
    }

    @Test
    void selectedLibraryCoreVisibility_shouldKeepPromotingRejectedPausedContract() {
        assertThat(policy.matchesSelectedLibraryCoreVisibility(1, true, null, ProductBizStatus.APPROVED.name(), false)).isTrue();
        assertThat(policy.matchesSelectedLibraryCoreVisibility(0, true, null, ProductBizStatus.APPROVED.name(), false)).isFalse();
        assertThat(policy.matchesSelectedLibraryCoreVisibility(1, false, null, ProductBizStatus.APPROVED.name(), false)).isFalse();
        assertThat(policy.matchesSelectedLibraryCoreVisibility(1, true, 3, ProductBizStatus.APPROVED.name(), false)).isFalse();
        assertThat(policy.matchesSelectedLibraryCoreVisibility(1, true, null, ProductBizStatus.REJECTED.name(), false)).isFalse();
        assertThat(policy.matchesSelectedLibraryCoreVisibility(1, true, null, ProductBizStatus.APPROVED.name(), true)).isFalse();
        assertThat(policy.matchesSelectedLibraryCoreVisibility(1, true, null, "UNKNOWN", false)).isTrue();
        assertThat(policy.isLocalRejectedProductState(3, ProductBizStatus.APPROVED.name())).isTrue();
        assertThat(policy.isLocalRejectedProductState(null, "UNKNOWN")).isFalse();
    }

    @Test
    void activityProductStatusCounts_shouldNormalizeRawAggregateContract() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("total", 12);
        raw.put("pendingReview", 1L);
        raw.put("promoting", new BigDecimal("2"));
        raw.put("rejected", -3);
        raw.put("terminated", null);
        raw.put("expired", "6");

        Map<String, Object> counts = policy.normalizeActivityProductStatusCounts(raw);

        assertThat(counts).containsExactly(
                entry("total", 12L),
                entry("pendingReview", 1L),
                entry("promoting", 2L),
                entry("rejected", 0L),
                entry("terminated", 0L),
                entry("expired", 0L));
        assertThat(policy.normalizeActivityProductStatusCounts(null)).containsExactly(
                entry("total", 0L),
                entry("pendingReview", 0L),
                entry("promoting", 0L),
                entry("rejected", 0L),
                entry("terminated", 0L),
                entry("expired", 0L));
    }

    private static ProductDisplayRelationInput eligible(
            String activityId,
            long commission,
            boolean traffic,
            LocalDateTime shelfTime) {
        return relation(activityId, commission, traffic, shelfTime, NOW.plusDays(30));
    }

    private static ProductDisplayRelationInput eligibleWithServiceFee(
            String activityId,
            long commission,
            boolean traffic,
            String serviceFee,
            LocalDateTime shelfTime) {
        UUID relationId = UUID.randomUUID();
        return new ProductDisplayRelationInput(
                relationId,
                PRODUCT_ID,
                activityId,
                "APPROVED",
                1,
                NOW.minusMonths(1),
                NOW.plusDays(30),
                ProductDisplayStatus.HIDDEN.name(),
                shelfTime,
                null,
                null,
                BigDecimal.valueOf(commission),
                new BigDecimal(serviceFee),
                traffic,
                false,
                null,
                null,
                null,
                true,
                false,
                false,
                false,
                null,
                shelfTime,
                3);
    }

    private static ProductDisplayRelationInput displaying(
            String activityId,
            long commission,
            boolean traffic,
            LocalDateTime shelfTime,
            LocalDateTime firstDisplayedAt) {
        UUID relationId = UUID.randomUUID();
        return new ProductDisplayRelationInput(
                relationId,
                PRODUCT_ID,
                activityId,
                "APPROVED",
                1,
                NOW.minusMonths(1),
                NOW.plusDays(30),
                ProductDisplayStatus.DISPLAYING.name(),
                shelfTime,
                firstDisplayedAt,
                firstDisplayedAt,
                BigDecimal.valueOf(commission),
                BigDecimal.ZERO,
                traffic,
                false,
                null,
                null,
                null,
                true,
                false,
                false,
                false,
                null,
                shelfTime,
                3);
    }

    private static ProductDisplayRelationInput relation(
            String activityId,
            long commission,
            boolean traffic,
            LocalDateTime shelfTime,
            LocalDateTime activityEndTime) {
        UUID relationId = UUID.randomUUID();
        return new ProductDisplayRelationInput(
                relationId,
                PRODUCT_ID,
                activityId,
                "APPROVED",
                1,
                NOW.minusMonths(1),
                activityEndTime,
                ProductDisplayStatus.HIDDEN.name(),
                shelfTime,
                null,
                null,
                BigDecimal.valueOf(commission),
                BigDecimal.ZERO,
                traffic,
                false,
                null,
                null,
                null,
                true,
                false,
                false,
                false,
                null,
                shelfTime,
                3);
    }
}
