package com.colonel.saas.domain.sample.event;

import com.colonel.saas.constant.SampleDomainEventTypes;
import com.colonel.saas.domain.event.OutboxEventAppender;
import com.colonel.saas.entity.SampleRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleDomainEventPublisherTest {

    @Mock private OutboxEventAppender outboxEventAppender;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void publishSampleCreated_shouldAppendOutboxAndPublishSpringEvent() {
        SampleDomainEventPublisher publisher = new SampleDomainEventPublisher(outboxEventAppender, applicationEventPublisher);
        SampleRequest sample = new SampleRequest();
        UUID id = UUID.randomUUID();
        sample.setId(id);
        sample.setProductId(UUID.randomUUID());
        sample.setTalentId(UUID.randomUUID());
        sample.setChannelUserId(UUID.randomUUID());
        sample.setStatus(1);

        when(outboxEventAppender.appendIfAbsent(
                eq("SampleCreated:" + id),
                eq(SampleDomainEventTypes.SAMPLE_CREATED),
                eq(OutboxEventAppender.AGGREGATE_SAMPLE),
                eq(id.toString()),
                eq(1),
                any(),
                any(),
                eq(null))).thenReturn(UUID.randomUUID());

        publisher.publishSampleCreated(sample, "商品A", "渠道A", UUID.randomUUID(), "act-1");

        verify(applicationEventPublisher).publishEvent(any(SampleCreatedEvent.class));
    }

    @Test
    void publishSampleCreated_outboxFailure_shouldNotThrow() {
        SampleDomainEventPublisher publisher = new SampleDomainEventPublisher(outboxEventAppender, applicationEventPublisher);
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setStatus(1);
        doThrow(new RuntimeException("db down")).when(outboxEventAppender).appendIfAbsent(
                any(), any(), any(), any(), any(int.class), any(), any(), any());
        publisher.publishSampleCreated(sample, null, null, null, null);
        verify(applicationEventPublisher).publishEvent(any(SampleCreatedEvent.class));
    }

    @Test
    void publishSampleCompleted_shouldUseIdempotentEventKey() {
        SampleDomainEventPublisher publisher = new SampleDomainEventPublisher(outboxEventAppender, applicationEventPublisher);
        SampleRequest sample = new SampleRequest();
        UUID id = UUID.randomUUID();
        sample.setId(id);
        sample.setProductId(UUID.randomUUID());
        sample.setTalentId(UUID.randomUUID());
        sample.setChannelUserId(UUID.randomUUID());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        publisher.publishSampleCompleted(sample, "ORDER-1", null);
        verify(outboxEventAppender).appendIfAbsent(
                keyCaptor.capture(),
                eq(SampleDomainEventTypes.SAMPLE_COMPLETED),
                eq(OutboxEventAppender.AGGREGATE_SAMPLE),
                eq(id.toString()),
                eq(1),
                any(),
                eq(null),
                eq(null));
        assertThat(keyCaptor.getValue()).isEqualTo("SampleCompleted:" + id + ":ORDER-1");
    }
}
