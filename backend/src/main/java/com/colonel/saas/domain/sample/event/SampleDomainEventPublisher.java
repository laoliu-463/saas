package com.colonel.saas.domain.sample.event;

import com.colonel.saas.constant.SampleDomainEventTypes;
import com.colonel.saas.domain.event.OutboxEventAppender;
import com.colonel.saas.entity.SampleRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SampleDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SampleDomainEventPublisher.class);
    private static final int EVENT_VERSION = 1;

    private final OutboxEventAppender outboxEventAppender;
    private final ApplicationEventPublisher applicationEventPublisher;

    public SampleDomainEventPublisher(
            OutboxEventAppender outboxEventAppender,
            ApplicationEventPublisher applicationEventPublisher) {
        this.outboxEventAppender = outboxEventAppender;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishSampleCreated(
            SampleRequest sample,
            String productName,
            String channelName,
            UUID recruiterId,
            String partnerId) {
        if (sample == null || sample.getId() == null) {
            return;
        }
        LocalDateTime occurredAt = sample.getCreateTime() != null ? sample.getCreateTime() : LocalDateTime.now();
        SampleCreatedEvent event = new SampleCreatedEvent(
                sample.getId(),
                sample.getProductId(),
                productName,
                sample.getTalentId(),
                sample.getTalentNickname(),
                sample.getChannelUserId(),
                channelName,
                recruiterId,
                partnerId,
                sample.getStatus(),
                occurredAt);
        Map<String, Object> payload = mapOf(
                "sampleRequestId", sample.getId().toString(),
                "productId", uuid(sample.getProductId()),
                "productName", productName,
                "talentId", uuid(sample.getTalentId()),
                "talentName", sample.getTalentNickname(),
                "channelId", uuid(sample.getChannelUserId()),
                "channelName", channelName,
                "recruiterId", uuid(recruiterId),
                "partnerId", partnerId,
                "status", sample.getStatus(),
                "createdAt", occurredAt.toString());
        appendOutbox("SampleCreated:" + sample.getId(), SampleDomainEventTypes.SAMPLE_CREATED, sample.getId(), payload, sample.getUserId());
        publishSpring(event);
    }

    public void publishSampleApproved(SampleRequest sample, UUID recruiterId, UUID approvedBy, LocalDateTime approvedAt) {
        if (sample == null || sample.getId() == null) {
            return;
        }
        LocalDateTime at = approvedAt != null ? approvedAt : LocalDateTime.now();
        SampleApprovedEvent event = new SampleApprovedEvent(
                sample.getId(), sample.getProductId(), sample.getTalentId(),
                sample.getChannelUserId(), recruiterId, approvedBy, at);
        Map<String, Object> payload = mapOf(
                "sampleRequestId", sample.getId().toString(),
                "productId", uuid(sample.getProductId()),
                "talentId", uuid(sample.getTalentId()),
                "channelId", uuid(sample.getChannelUserId()),
                "recruiterId", uuid(recruiterId),
                "approvedBy", uuid(approvedBy),
                "approvedAt", at.toString());
        appendOutbox("SampleApproved:" + sample.getId() + ":" + at, SampleDomainEventTypes.SAMPLE_APPROVED, sample.getId(), payload, approvedBy);
        publishSpring(event);
    }

    public void publishSampleRejected(SampleRequest sample, UUID rejectedBy, String reason, LocalDateTime rejectedAt) {
        if (sample == null || sample.getId() == null) {
            return;
        }
        LocalDateTime at = rejectedAt != null ? rejectedAt : LocalDateTime.now();
        SampleRejectedEvent event = new SampleRejectedEvent(sample.getId(), rejectedBy, reason, at);
        Map<String, Object> payload = mapOf(
                "sampleRequestId", sample.getId().toString(),
                "rejectedBy", uuid(rejectedBy),
                "rejectedReason", reason,
                "rejectedAt", at.toString());
        appendOutbox("SampleRejected:" + sample.getId() + ":" + at, SampleDomainEventTypes.SAMPLE_REJECTED, sample.getId(), payload, rejectedBy);
        publishSpring(event);
    }

    public void publishSampleShipped(SampleRequest sample, UUID shippedBy, LocalDateTime shippedAt) {
        if (sample == null || sample.getId() == null || !StringUtils.hasText(sample.getTrackingNo())) {
            return;
        }
        LocalDateTime at = shippedAt != null ? shippedAt : LocalDateTime.now();
        String trackingNo = sample.getTrackingNo().trim();
        String eventKey = "SampleShipped:" + sample.getId() + ":" + trackingNo;
        SampleShippedEvent event = new SampleShippedEvent(
                sample.getId(), sample.getShipperCode(), trackingNo, shippedBy, at);
        Map<String, Object> payload = mapOf(
                "sampleRequestId", sample.getId().toString(),
                "logisticsCompany", sample.getShipperCode(),
                "trackingNo", trackingNo,
                "shippedBy", uuid(shippedBy),
                "shippedAt", at.toString());
        appendOutbox(eventKey, SampleDomainEventTypes.SAMPLE_SHIPPED, sample.getId(), payload, shippedBy);
        publishSpring(event);
    }

    public void publishSampleSigned(SampleRequest sample, LocalDateTime signedAt) {
        if (sample == null || sample.getId() == null) {
            return;
        }
        LocalDateTime at = signedAt != null ? signedAt : LocalDateTime.now();
        String trackingNo = sample.getTrackingNo();
        String eventKey = "SampleSigned:" + sample.getId() + ":" + (trackingNo == null ? "none" : trackingNo.trim());
        SampleSignedEvent event = new SampleSignedEvent(sample.getId(), trackingNo, at);
        Map<String, Object> payload = mapOf(
                "sampleRequestId", sample.getId().toString(),
                "trackingNo", trackingNo,
                "signedAt", at.toString());
        appendOutbox(eventKey, SampleDomainEventTypes.SAMPLE_SIGNED, sample.getId(), payload, null);
        publishSpring(event);
    }

    public void publishSampleCompleted(SampleRequest sample, String orderId, LocalDateTime completedAt) {
        if (sample == null || sample.getId() == null) {
            return;
        }
        LocalDateTime at = completedAt != null ? completedAt : LocalDateTime.now();
        String orderKey = StringUtils.hasText(orderId) ? orderId.trim() : "unknown";
        String eventKey = "SampleCompleted:" + sample.getId() + ":" + orderKey;
        SampleCompletedEvent event = new SampleCompletedEvent(
                sample.getId(), orderKey, sample.getProductId(), sample.getTalentId(), sample.getChannelUserId(), at);
        Map<String, Object> payload = mapOf(
                "sampleRequestId", sample.getId().toString(),
                "orderId", orderKey,
                "productId", uuid(sample.getProductId()),
                "talentId", uuid(sample.getTalentId()),
                "channelId", uuid(sample.getChannelUserId()),
                "completedAt", at.toString());
        appendOutbox(eventKey, SampleDomainEventTypes.SAMPLE_COMPLETED, sample.getId(), payload, null);
        publishSpring(event);
    }

    public void publishSampleClosed(SampleRequest sample, String closeReason, LocalDateTime closedAt) {
        if (sample == null || sample.getId() == null) {
            return;
        }
        LocalDateTime at = closedAt != null ? closedAt : LocalDateTime.now();
        SampleClosedEvent event = new SampleClosedEvent(sample.getId(), closeReason, at);
        Map<String, Object> payload = mapOf(
                "sampleRequestId", sample.getId().toString(),
                "closeReason", closeReason,
                "closedAt", at.toString());
        appendOutbox("SampleClosed:" + sample.getId() + ":" + at, SampleDomainEventTypes.SAMPLE_CLOSED, sample.getId(), payload, null);
        publishSpring(event);
    }

    private void appendOutbox(
            String eventKey,
            String eventType,
            UUID sampleRequestId,
            Map<String, Object> payload,
            UUID operatorId) {
        try {
            outboxEventAppender.appendIfAbsent(
                    eventKey,
                    eventType,
                    OutboxEventAppender.AGGREGATE_SAMPLE,
                    sampleRequestId.toString(),
                    EVENT_VERSION,
                    payload,
                    operatorId,
                    null);
        } catch (Exception ex) {
            log.warn("Outbox append failed: eventType={}, sampleRequestId={}", eventType, sampleRequestId, ex);
        }
    }

    private void publishSpring(Object event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Spring local event publish failed: eventClass={}", event.getClass().getSimpleName(), ex);
        }
    }

    public void republishSpringEvent(String eventType, String payloadJson) {
        try {
            applicationEventPublisher.publishEvent(Map.of("eventType", eventType, "payload", payloadJson));
        } catch (Exception ex) {
            log.warn("Spring republish failed for eventType={}", eventType, ex);
        }
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private static String uuid(UUID id) {
        return id == null ? null : id.toString();
    }
}
