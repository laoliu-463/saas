package com.colonel.saas.domain.order.policy;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionPolicy.RecruiterLookup;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDefaultAttributionPolicyTest {

    @Test
    void resolve_pickSourceHit_shouldAttributeChannel() {
        UUID channelUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(channelUserId);
        mapping.setDeptId(deptId);
        mapping.setActivityId("act-1");

        OrderAttributionInput input = new OrderAttributionInput("prod-1", "act-1", "ps-1", null, "uid-1", null);
        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input,
                mapping,
                new RecruiterLookup(null, null, false));

        assertThat(result.defaultChannelUserId()).isEqualTo(channelUserId);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
    }

    @Test
    void resolve_emptyPickSource_shouldBeUnattributedNoPickSource() {
        OrderAttributionInput input = new OrderAttributionInput("prod-1", "act-1", null, null, null, null);
        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input, null, new RecruiterLookup(null, null, false));

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_UNATTRIBUTED);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_NO_PICK_SOURCE);
    }

    @Test
    void resolve_mappingNotFound_shouldBeUnattributedMapping() {
        OrderAttributionInput input = new OrderAttributionInput("prod-1", "act-1", "ps-miss", null, null, null);
        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input, null, new RecruiterLookup(null, null, false));

        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_MAPPING_NOT_FOUND);
    }

    @Test
    void resolve_productAssignee_shouldSetDefaultRecruiter() {
        UUID recruiterId = UUID.randomUUID();
        OrderAttributionInput input = new OrderAttributionInput("prod-1", "act-1", null, null, null, null);
        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input,
                null,
                new RecruiterLookup(recruiterId, UUID.randomUUID(), false));

        assertThat(result.defaultRecruiterId()).isEqualTo(recruiterId);
    }

    @Test
    void resolve_emptyProductAssignee_shouldFallbackActivityDefaultRecruiter() {
        UUID activityRecruiter = UUID.randomUUID();
        OrderAttributionInput input = new OrderAttributionInput("prod-1", "act-1", null, null, null, null);
        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input,
                null,
                new RecruiterLookup(null, activityRecruiter, false));

        assertThat(result.defaultRecruiterId()).isEqualTo(activityRecruiter);
    }

    @Test
    void resolve_recruiterLookupFailed_shouldNotBlockChannelResolution() {
        UUID channelUserId = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(channelUserId);
        mapping.setDeptId(UUID.randomUUID());

        OrderAttributionInput input = new OrderAttributionInput("prod-1", "act-1", "ps-1", null, null, null);
        OrderDefaultAttributionResult result = OrderDefaultAttributionPolicy.resolve(
                input,
                mapping,
                new RecruiterLookup(null, null, true));

        assertThat(result.defaultRecruiterId()).isNull();
        assertThat(result.defaultChannelUserId()).isEqualTo(channelUserId);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
    }

    @Test
    void applyToOrder_shouldWriteOrderFields() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductName("商品A");

        UUID channelUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();

        OrderDefaultAttributionResult result = OrderDefaultAttributionResult.attributedChannel(
                channelUserId,
                deptId,
                talentId,
                "talent-1",
                "act-new",
                recruiterId,
                AttributionService.REASON_ATTRIBUTED);

        OrderDefaultAttributionPolicy.applyToOrder(order, result, "达人A");

        assertThat(order.getChannelUserId()).isEqualTo(channelUserId);
        assertThat(order.getColonelUserId()).isEqualTo(recruiterId);
        assertThat(order.getTalentId()).isEqualTo(talentId);
        assertThat(order.getAttributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(order.getTalentName()).isEqualTo("达人A");
    }

    @Test
    void fromRawPayload_shouldBuildInput() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("prod-1");
        order.setPickSource("ps-1");
        order.setActivityId("act-old");

        OrderAttributionInput input = OrderAttributionInput.from(
                order,
                Map.of("activity_id", "act-new", "author_id", "uid-9"));

        assertThat(input.productId()).isEqualTo("prod-1");
        assertThat(input.pickSource()).isEqualTo("ps-1");
        assertThat(input.activityId()).isEqualTo("act-new");
        assertThat(input.talentUid()).isEqualTo("uid-9");
    }

    @Test
    void classifyUnattributedRemark_shouldBucketKnownReasons() {
        assertThat(OrderDefaultAttributionPolicy.classifyUnattributedRemark(
                AttributionService.REASON_NO_PICK_SOURCE))
                .isEqualTo(OrderDefaultAttributionPolicy.UnattributedBucket.NO_PICK_SOURCE);
        assertThat(OrderDefaultAttributionPolicy.classifyUnattributedRemark(
                AttributionService.REASON_MAPPING_NOT_FOUND))
                .isEqualTo(OrderDefaultAttributionPolicy.UnattributedBucket.NO_MAPPING);
    }

    @Test
    void toLegacyResult_shouldNotReferenceExclusiveTypes() {
        OrderDefaultAttributionResult result = OrderDefaultAttributionResult.unattributed(
                null, null, "act-1", UUID.randomUUID(), AttributionService.REASON_NO_PICK_SOURCE);
        AttributionService.AttributionResult legacy = OrderDefaultAttributionPolicy.toLegacyResult(result);
        assertThat(legacy.colonelUserId()).isNotNull();
        assertThat(legacy.attributionStatus()).isEqualTo(AttributionService.STATUS_UNATTRIBUTED);
    }
}
