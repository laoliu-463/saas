package com.colonel.saas.service;

import com.colonel.saas.constant.ProductDomainEventTypes;
import com.colonel.saas.domain.event.OutboxEventAppender;
import com.colonel.saas.domain.product.event.ActivitySyncCompletedEvent;
import com.colonel.saas.domain.product.event.PartnerSyncCompletedEvent;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductDomainEventPublisherTest {

    @Mock
    private OutboxEventAppender outboxEventAppender;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ProductDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ProductDomainEventPublisher(outboxEventAppender, applicationEventPublisher);
    }

    @Test
    void publishProductListed_shouldAppendOutboxEvent() {
        UUID stateId = UUID.randomUUID();
        publisher.publishProductListed("10001", "9001", stateId, UUID.randomUUID(), 3, "RULE_ENGINE");

        verify(outboxEventAppender).appendIfAbsent(
                eq("ProductListed:" + stateId + ":3"),
                eq(ProductDomainEventTypes.PRODUCT_LISTED),
                eq(OutboxEventAppender.AGGREGATE_PRODUCT),
                eq("9001"),
                eq(1),
                any(Map.class),
                any(),
                eq(null));
    }

    @Test
    void publishProductHidden_shouldAppendOutboxWithReason() {
        UUID stateId = UUID.randomUUID();
        publisher.publishProductHidden("10001", "9001", stateId, "ACTIVITY_EXPIRED", 3);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxEventAppender).appendIfAbsent(
                keyCaptor.capture(),
                eq(ProductDomainEventTypes.PRODUCT_HIDDEN),
                eq(OutboxEventAppender.AGGREGATE_PRODUCT),
                eq("9001"),
                eq(1),
                any(Map.class),
                eq(null),
                eq(null));
        assertThat(keyCaptor.getValue()).contains("ACTIVITY_EXPIRED");
    }

    @Test
    void publishActivitySyncCompleted_shouldEmitSpringEventWithPayload() {
        UUID operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        publisher.publishActivitySyncCompleted("10001", "五一活动", "FULL", 3, 9, 1, "SUCCESS", operatorId);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ActivitySyncCompletedEvent.class);
        ActivitySyncCompletedEvent event = (ActivitySyncCompletedEvent) captor.getValue();
        assertThat(event.activityId()).isEqualTo("10001");
        assertThat(event.activityName()).isEqualTo("五一活动");
        assertThat(event.syncType()).isEqualTo("FULL");
        assertThat(event.createdCount()).isEqualTo(3);
        assertThat(event.updatedCount()).isEqualTo(9);
        assertThat(event.skippedCount()).isEqualTo(1);
        assertThat(event.syncStatus()).isEqualTo("SUCCESS");
        assertThat(event.operatorId()).isEqualTo(operatorId);
    }

    @Test
    void publishPartnerSyncCompleted_shouldEmitSpringEventWithPayload() {
        publisher.publishPartnerSyncCompleted(
                "7109679864001364265",
                "张团长",
                "COLONEL",
                "PRODUCT_SNAPSHOT",
                "SUCCESS",
                true,
                false);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PartnerSyncCompletedEvent.class);
        PartnerSyncCompletedEvent event = (PartnerSyncCompletedEvent) captor.getValue();
        assertThat(event.partnerId()).isEqualTo("7109679864001364265");
        assertThat(event.partnerName()).isEqualTo("张团长");
        assertThat(event.partnerType()).isEqualTo("COLONEL");
        assertThat(event.source()).isEqualTo("PRODUCT_SNAPSHOT");
        assertThat(event.created()).isTrue();
        assertThat(event.updated()).isFalse();
    }

    @Test
    void publishActivitySyncCompleted_shouldSwallowSpringPublisherFailure() {
        doThrow(new IllegalStateException("listener down"))
                .when(applicationEventPublisher).publishEvent(any());

        publisher.publishActivitySyncCompleted("10001", "活动", "FULL", 1, 0, 0, "SUCCESS", null);
    }
}
