package com.colonel.saas.architecture;

import com.colonel.saas.domain.order.event.OrderEventPayloadMapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.event.OrderSyncedEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构守门人：保证 {@code OrderSyncedEvent} 的 {@code extraData} 一定携带
 * 双维度归属状态（{@code channel_attribution_status} / {@code recruiter_attribution_status}），
 * 避免业绩域 / 看板 / 重放链路在事件载荷中拿不到这两个事实。
 *
 * <p>为什么不直接进 record 字段：{@code OrderSyncedEvent} 已经是 36 个参数的 record，
 * 加字段会触发调用方 / 反序列化器 / Outbox 序列化全链路改动。当前切片先走 {@code extraData} 通道，
 * 下一切片再决定是否升级为 record 字段。</p>
 */
class OrderSyncedEventDualDimensionPayloadContractTest {

    private static final String CHANNEL_KEY = "channel_attribution_status";
    private static final String RECRUITER_KEY = "recruiter_attribution_status";

    private final OrderEventPayloadMapper mapper = new OrderEventPayloadMapper();

    @Test
    void toOrderSyncedEvent_shouldAlwaysIncludeBothAttributionStatusesInExtraData() {
        ColonelsettlementOrder order = newOrderWithDualStatuses(
                "CHANNEL_ATTRIBUTED", "RECRUITER_ATTRIBUTED");

        OrderSyncedEvent event = mapper.toOrderSyncedEvent(order, true);

        assertThat(event.extraData())
                .as("OrderSyncedEvent.extraData must include both dual-dimension status keys")
                .isNotNull()
                .containsEntry(CHANNEL_KEY, "CHANNEL_ATTRIBUTED")
                .containsEntry(RECRUITER_KEY, "RECRUITER_ATTRIBUTED");
    }

    @Test
    void toOrderSyncedEvent_shouldExposeChannelUnattributedWhenChannelUserIdMissing() {
        ColonelsettlementOrder order = newOrderWithDualStatuses(null, "RECRUITER_ATTRIBUTED");
        order.setChannelUserId(null);
        order.setChannelDeptId(null);

        OrderSyncedEvent event = mapper.toOrderSyncedEvent(order, true);

        assertThat(event.extraData()).containsEntry(CHANNEL_KEY, "CHANNEL_UNATTRIBUTED");
        assertThat(event.extraData()).containsEntry(RECRUITER_KEY, "RECRUITER_ATTRIBUTED");
    }

    @Test
    void toOrderSyncedEvent_shouldExposeRecruiterUnattributedWhenColonelUserIdMissing() {
        ColonelsettlementOrder order = newOrderWithDualStatuses("CHANNEL_ATTRIBUTED", null);
        order.setColonelUserId(null);
        order.setUserId(order.getChannelUserId());

        OrderSyncedEvent event = mapper.toOrderSyncedEvent(order, true);

        assertThat(event.extraData()).containsEntry(CHANNEL_KEY, "CHANNEL_ATTRIBUTED");
        assertThat(event.extraData()).containsEntry(RECRUITER_KEY, "RECRUITER_UNATTRIBUTED");
        assertThat(event.defaultRecruiterId()).isNull();
        assertThat(event.recruiterAttribution()).isNull();
    }

    @Test
    void toOrderSyncedEvent_shouldNotMutateOriginalExtraDataMap() {
        Map<String, Object> originalExtra = Map.of("author_id", "T-100");
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD-IMMUTABLE-1");
        order.setId(UUID.randomUUID());
        order.setChannelUserId(UUID.randomUUID());
        order.setColonelUserId(UUID.randomUUID());
        order.setAttributionStatus("ATTRIBUTED");
        order.setChannelAttributionStatus("CHANNEL_ATTRIBUTED");
        order.setRecruiterAttributionStatus("RECRUITER_ATTRIBUTED");
        order.setExtraData(originalExtra);

        OrderSyncedEvent event = mapper.toOrderSyncedEvent(order, true);

        assertThat(event.extraData())
                .as("Mapper must not mutate caller's extraData reference")
                .isNotSameAs(originalExtra);
        assertThat(originalExtra)
                .as("Original extraData must remain free of dual-dimension keys")
                .doesNotContainKey(CHANNEL_KEY)
                .doesNotContainKey(RECRUITER_KEY);
    }

    private static ColonelsettlementOrder newOrderWithDualStatuses(String channelStatus, String recruiterStatus) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD-DUAL-" + UUID.randomUUID());
        order.setId(UUID.randomUUID());
        order.setProductId("P-1");
        order.setActivityId("A-1");
        order.setShopId(1001L);
        order.setTalentId(UUID.randomUUID());
        order.setChannelUserId(UUID.randomUUID());
        order.setColonelUserId(UUID.randomUUID());
        order.setPickSource("usr_ABC_1712000000");
        order.setAttributionStatus("ATTRIBUTED");
        order.setChannelAttributionStatus(channelStatus);
        order.setRecruiterAttributionStatus(recruiterStatus);
        order.setOrderAmount(100L);
        order.setSettleAmount(80L);
        order.setEstimateServiceFee(10L);
        order.setEffectiveServiceFee(9L);
        order.setOrderStatus(3);
        order.setExtraData(Map.of("author_id", "T-100"));
        return order;
    }
}
