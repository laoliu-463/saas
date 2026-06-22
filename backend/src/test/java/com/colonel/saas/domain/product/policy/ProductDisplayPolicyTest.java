package com.colonel.saas.domain.product.policy;

import com.colonel.saas.constant.ProductDisplayStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
    void legacyStatusFour_shouldNotFallbackToPendingReview() {
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
