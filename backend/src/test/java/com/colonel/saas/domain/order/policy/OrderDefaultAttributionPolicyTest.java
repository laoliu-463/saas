package com.colonel.saas.domain.order.policy;

import com.colonel.saas.domain.order.policy.OrderDefaultAttributionPolicy.RecruiterLookup;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution.Status;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
import com.colonel.saas.domain.shared.attribution.AttributionSource;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.AttributionService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDefaultAttributionPolicyTest {

    @Test
    void recruiterLinkShouldWinBeforeActivityRecruiter() {
        UUID linkRecruiter = UUID.randomUUID();
        UUID activityRecruiter = UUID.randomUUID();

        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input(),
                unique(linkRecruiter, AttributionOwnerType.RECRUITER, AttributionSource.PICK_SOURCE),
                new RecruiterLookup(activityRecruiter, false));

        assertThat(result.defaultRecruiterId()).isEqualTo(linkRecruiter);
        assertThat(result.defaultChannelUserId()).isNull();
        assertThat(result.recruiterAttributionSource()).isEqualTo(AttributionSource.PICK_SOURCE);
        assertThat(result.channelAttributionSource()).isEqualTo(AttributionSource.UNATTRIBUTED);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
    }

    @Test
    void channelLinkShouldWriteChannelAndFallbackRecruiterToActivity() {
        UUID channelUser = UUID.randomUUID();
        UUID activityRecruiter = UUID.randomUUID();

        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input(),
                unique(channelUser, AttributionOwnerType.CHANNEL, AttributionSource.NATIVE_UNIQUE_LINK_OWNER),
                new RecruiterLookup(activityRecruiter, false));

        assertThat(result.defaultChannelUserId()).isEqualTo(channelUser);
        assertThat(result.channelDeptId()).isNotNull();
        assertThat(result.defaultRecruiterId()).isEqualTo(activityRecruiter);
        assertThat(result.channelAttributionSource()).isEqualTo(AttributionSource.NATIVE_UNIQUE_LINK_OWNER);
        assertThat(result.recruiterAttributionSource()).isEqualTo(AttributionSource.ACTIVITY_OWNER);
    }

    @Test
    void noLinkShouldFallbackToActivityRecruiter() {
        UUID activityRecruiter = UUID.randomUUID();

        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input(), notFound(), new RecruiterLookup(activityRecruiter, false));

        assertThat(result.defaultChannelUserId()).isNull();
        assertThat(result.defaultRecruiterId()).isEqualTo(activityRecruiter);
        assertThat(result.recruiterAttributionSource()).isEqualTo(AttributionSource.ACTIVITY_OWNER);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
    }

    @Test
    void ambiguousLinkShouldNotSetLinkOwnerButMayFallbackToActivityRecruiter() {
        UUID activityRecruiter = UUID.randomUUID();
        OrderLinkAttributionResolution ambiguous = new OrderLinkAttributionResolution(
                Status.AMBIGUOUS, null, null, null, AttributionSource.AMBIGUOUS,
                "MULTIPLE_ATTRIBUTION_OWNERS", true, false, null);

        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input(), ambiguous, new RecruiterLookup(activityRecruiter, false));

        assertThat(result.defaultChannelUserId()).isNull();
        assertThat(result.defaultRecruiterId()).isEqualTo(activityRecruiter);
        assertThat(result.channelAttributionSource()).isEqualTo(AttributionSource.AMBIGUOUS);
        assertThat(result.recruiterAttributionSource()).isEqualTo(AttributionSource.ACTIVITY_OWNER);
    }

    @Test
    void allEmptyShouldRemainUnattributed() {
        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input(), notFound(), new RecruiterLookup(null, false));

        assertThat(result.defaultChannelUserId()).isNull();
        assertThat(result.defaultRecruiterId()).isNull();
        assertThat(result.channelAttributionSource()).isEqualTo(AttributionSource.UNATTRIBUTED);
        assertThat(result.recruiterAttributionSource()).isEqualTo(AttributionSource.UNATTRIBUTED);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_UNATTRIBUTED);
    }

    @Test
    void recruiterLookupFailureShouldNotBlockChannelResolution() {
        UUID channelUser = UUID.randomUUID();

        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input(),
                unique(channelUser, AttributionOwnerType.CHANNEL, AttributionSource.PICK_SOURCE),
                new RecruiterLookup(null, true));

        assertThat(result.defaultChannelUserId()).isEqualTo(channelUser);
        assertThat(result.defaultRecruiterId()).isNull();
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
    }

    @Test
    void applyToOrderShouldWriteSeparateChannelAndRecruiterFacts() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductName("商品A");
        UUID channelUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        OrderDefaultAttributionResult result = OrderDefaultAttributionResult.attributed(
                channelUserId, deptId, recruiterId,
                AttributionSource.PICK_SOURCE, AttributionSource.ACTIVITY_OWNER,
                talentId, "talent-1", "act-new", null);

        OrderDefaultAttributionPolicy.applyToOrder(order, result, "达人A");

        assertThat(order.getChannelUserId()).isEqualTo(channelUserId);
        assertThat(order.getColonelUserId()).isEqualTo(recruiterId);
        assertThat(order.getChannelAttributionSource()).isEqualTo(AttributionSource.PICK_SOURCE);
        assertThat(order.getRecruiterAttributionSource()).isEqualTo(AttributionSource.ACTIVITY_OWNER);
        assertThat(order.getTalentId()).isEqualTo(talentId);
        assertThat(order.getAttributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(order.getTalentName()).isEqualTo("达人A");
    }

    @Test
    void classifyUnattributedRemarkShouldBucketKnownReasons() {
        assertThat(OrderDefaultAttributionPolicy.classifyUnattributedRemark(
                AttributionService.REASON_NO_PICK_SOURCE))
                .isEqualTo(OrderDefaultAttributionPolicy.UnattributedBucket.NO_PICK_SOURCE);
        assertThat(OrderDefaultAttributionPolicy.classifyUnattributedRemark(
                AttributionService.REASON_MAPPING_NOT_FOUND))
                .isEqualTo(OrderDefaultAttributionPolicy.UnattributedBucket.NO_MAPPING);
    }

    @Test
    void toLegacyResultShouldPreserveRecruiterForRecruiterOnlyAttribution() {
        UUID recruiterId = UUID.randomUUID();
        OrderDefaultAttributionResult result = OrderDefaultAttributionResult.attributed(
                null, null, recruiterId,
                AttributionSource.UNATTRIBUTED, AttributionSource.PICK_SOURCE,
                null, null, "act-1", null);

        AttributionService.AttributionResult legacy = OrderDefaultAttributionPolicy.toLegacyResult(result);

        assertThat(legacy.channelUserId()).isNull();
        assertThat(legacy.colonelUserId()).isEqualTo(recruiterId);
        assertThat(legacy.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
    }

    private OrderAttributionInput input() {
        return new OrderAttributionInput("prod-1", "act-1", "ps-1", null, "uid-1", null);
    }

    private OrderLinkAttributionResolution unique(UUID userId, AttributionOwnerType ownerType, String source) {
        return new OrderLinkAttributionResolution(
                Status.UNIQUE, userId, UUID.randomUUID(), ownerType, source,
                "UNIQUE_LINK_OWNER", true, false, null);
    }

    private OrderLinkAttributionResolution notFound() {
        return new OrderLinkAttributionResolution(
                Status.NOT_FOUND, null, null, null, AttributionSource.UNATTRIBUTED,
                "MAPPING_NOT_FOUND", false, false, null);
    }
}
