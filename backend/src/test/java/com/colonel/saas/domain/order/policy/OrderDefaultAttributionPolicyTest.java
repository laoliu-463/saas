package com.colonel.saas.domain.order.policy;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.AttributionService.AttributionResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDefaultAttributionPolicyTest {

    @Test
    void applyAttributionResult_shouldWriteOrderFields() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductName("商品A");
        order.setActivityId("act-old");

        UUID channelUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        UUID colonelUserId = UUID.randomUUID();

        AttributionResult result = AttributionResult.attributed(
                channelUserId,
                deptId,
                channelUserId,
                talentId,
                "talent-1",
                "act-new",
                colonelUserId,
                AttributionService.REASON_ATTRIBUTED,
                AttributionService.NativeMappingTrace.none());

        OrderDefaultAttributionPolicy.applyAttributionResult(order, result, order.getActivityId(), "达人A");

        assertThat(order.getChannelUserId()).isEqualTo(channelUserId);
        assertThat(order.getDeptId()).isEqualTo(deptId);
        assertThat(order.getTalentId()).isEqualTo(talentId);
        assertThat(order.getActivityId()).isEqualTo("act-new");
        assertThat(order.getAttributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(order.getProductTitle()).isEqualTo("商品A");
        assertThat(order.getTalentName()).isEqualTo("达人A");
    }

    @Test
    void classifyUnattributedRemark_shouldBucketKnownReasons() {
        assertThat(OrderDefaultAttributionPolicy.classifyUnattributedRemark(
                AttributionService.REASON_NO_PICK_SOURCE))
                .isEqualTo(OrderDefaultAttributionPolicy.UnattributedBucket.NO_PICK_SOURCE);
        assertThat(OrderDefaultAttributionPolicy.classifyUnattributedRemark(
                AttributionService.REASON_MAPPING_NOT_FOUND))
                .isEqualTo(OrderDefaultAttributionPolicy.UnattributedBucket.NO_MAPPING);
        assertThat(OrderDefaultAttributionPolicy.classifyUnattributedRemark(
                AttributionService.REASON_PRODUCT_NOT_FOUND))
                .isEqualTo(OrderDefaultAttributionPolicy.UnattributedBucket.NONE);
    }
}
