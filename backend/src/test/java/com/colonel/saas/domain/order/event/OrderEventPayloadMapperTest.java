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
        order.setOrderStatus(3);
        order.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        order.setOrderCreateTime(orderCreateTime);
        order.setPayTime(payTime);
        order.setSettleTime(settleTime);
        order.setExtraData(Map.of("author_id", "T-100"));

        OrderSyncedEvent event = mapper.toOrderSyncedEvent(order, true);

        assertThat(event.orderId()).isEqualTo("ORD-1");
        assertThat(event.orderRowId()).isEqualTo(orderRowId);
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
        assertThat(event.occurredAt()).isNotNull();
    }
}
