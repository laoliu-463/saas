package com.colonel.saas.domain.order.event;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.event.OrderSyncedEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventPayloadMapperTest {

    private final OrderEventPayloadMapper mapper = new OrderEventPayloadMapper();

    @Test
    void toOrderSyncedEvent_shouldMapCoreFields() {
        UUID orderRowId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        UUID channelUserId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        LocalDateTime orderCreateTime = LocalDateTime.of(2026, 5, 31, 9, 0);
        LocalDateTime payTime = LocalDateTime.of(2026, 6, 1, 10, 30);
        LocalDateTime settleTime = LocalDateTime.of(2026, 6, 3, 11, 15);
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD-1");
        order.setId(orderRowId);
        order.setVersion(8);
        order.setProductId("P-1");
        order.setActivityId("A-1");
        order.setShopId(1001L);
        order.setTalentId(talentId);
        order.setChannelUserId(channelUserId);
        order.setColonelUserId(recruiterId);
        order.setPickSource("usr_ABC_1712000000");
        order.setAttributionStatus("ATTRIBUTED");
        order.setOrderAmount(100L);
        order.setSettleAmount(80L);
        order.setEstimateServiceFee(10L);
        order.setEffectiveServiceFee(9L);
        order.setEstimateServiceFeeExpense(3L);
        order.setEffectiveServiceFeeExpense(4L);
        order.setOrderStatus(3);
        order.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        order.setOrderCreateTime(orderCreateTime);
        order.setPayTime(payTime);
        order.setSettleTime(settleTime);
        order.setExtraData(Map.of("author_id", "T-100"));

        OrderSyncedEvent event = mapper.toOrderSyncedEvent(order, true);

        assertThat(event.orderId()).isEqualTo("ORD-1");
        assertThat(event.orderRowId()).isEqualTo(orderRowId);
        assertThat(event.orderVersion()).isEqualTo(8);
        assertThat(event.newlyInserted()).isTrue();
        assertThat(event.isUpdate()).isFalse();
        assertThat(event.talentUid()).isEqualTo("T-100");
        assertThat(event.talentId()).isEqualTo(talentId);
        assertThat(event.orderStatus()).isEqualTo(3);
        assertThat(event.productId()).isEqualTo("P-1");
        assertThat(event.activityId()).isEqualTo("A-1");
        assertThat(event.partnerId()).isEqualTo("1001");
        assertThat(event.defaultChannelId()).isEqualTo(channelUserId);
        assertThat(event.defaultRecruiterId()).isEqualTo(recruiterId);
        assertThat(event.recruiterAttribution()).isEqualTo("DEFAULT");
        assertThat(event.pickSource()).isEqualTo("usr_ABC_1712000000");
        assertThat(event.payTime()).isEqualTo(payTime);
        assertThat(event.settleTime()).isEqualTo(settleTime);
        assertThat(event.orderCreateTime()).isEqualTo(orderCreateTime);
        assertThat(event.estimateServiceFeeExpense()).isEqualTo(3L);
        assertThat(event.effectiveServiceFeeExpense()).isEqualTo(4L);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void toOrderRefundFactSyncedEvent_shouldMapKnownRefundFactsFromExtraDataWithoutInferringAmount() {
        UUID orderRowId = UUID.randomUUID();
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD-REFUND-1");
        order.setId(orderRowId);
        order.setOrderStatus(5);
        order.setFlowPoint("REFUND");
        order.setExtraData(Map.of(
                "refund_id", "REF-1",
                "refund_amount", 12800L));

        OrderRefundFactSyncedEvent event = mapper.toOrderRefundFactSyncedEvent(order, 3);

        assertThat(event.orderId()).isEqualTo("ORD-REFUND-1");
        assertThat(event.orderRowId()).isEqualTo(orderRowId);
        assertThat(event.refundId()).isEqualTo("REF-1");
        assertThat(event.refundAmount()).isEqualTo(12800L);
        assertThat(event.previousStatus()).isEqualTo(3);
        assertThat(event.status()).isEqualTo(5);
        assertThat(event.flowPoint()).isEqualTo("REFUND");
        assertThat(event.extraData()).containsEntry("refund_id", "REF-1");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void toOrderRefundFactSyncedEvent_shouldKeepUnknownRefundAmountNull() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD-REFUND-UNKNOWN");
        order.setId(UUID.randomUUID());
        order.setOrderStatus(4);
        order.setOrderAmount(999L);
        order.setExtraData(Map.of("refund_amount", "not-a-number"));

        OrderRefundFactSyncedEvent event = mapper.toOrderRefundFactSyncedEvent(order, 2);

        assertThat(event.refundAmount()).isNull();
        assertThat(event.status()).isEqualTo(4);
    }
}
