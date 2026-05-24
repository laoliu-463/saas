package com.colonel.saas.domain.product.event;

import com.colonel.saas.constant.ProductDomainEventTypes;
import com.colonel.saas.domain.event.OutboxEventAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductDomainEventPublisher.class);
    private static final int EVENT_VERSION = 1;

    private final OutboxEventAppender outboxEventAppender;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ProductDomainEventPublisher(
            OutboxEventAppender outboxEventAppender,
            ApplicationEventPublisher applicationEventPublisher) {
        this.outboxEventAppender = outboxEventAppender;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishProductListed(
            String activityId,
            String productId,
            UUID operationStateId,
            UUID operatorId,
            int displayRuleVersion,
            String displayReason) {
        Map<String, Object> payload = basePayload(activityId, productId, operationStateId);
        payload.put("displayRuleVersion", displayRuleVersion);
        payload.put("displayReason", displayReason);
        appendOutbox(
                "ProductListed:" + operationStateId + ":" + displayRuleVersion,
                ProductDomainEventTypes.PRODUCT_LISTED,
                OutboxEventAppender.AGGREGATE_PRODUCT,
                productId,
                payload,
                operatorId);
    }

    public void publishProductHidden(
            String activityId,
            String productId,
            UUID operationStateId,
            String reason,
            int displayRuleVersion) {
        Map<String, Object> payload = basePayload(activityId, productId, operationStateId);
        payload.put("hiddenReason", reason);
        payload.put("displayRuleVersion", displayRuleVersion);
        appendOutbox(
                "ProductHidden:" + operationStateId + ":" + reason,
                ProductDomainEventTypes.PRODUCT_HIDDEN,
                OutboxEventAppender.AGGREGATE_PRODUCT,
                productId,
                payload,
                null);
    }

    public void publishProductOwnerChanged(
            String activityId,
            String productId,
            UUID oldAssigneeId,
            UUID newAssigneeId,
            UUID operatorId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", activityId);
        payload.put("productId", productId);
        payload.put("oldAssigneeId", oldAssigneeId == null ? null : oldAssigneeId.toString());
        payload.put("newAssigneeId", newAssigneeId == null ? null : newAssigneeId.toString());
        payload.put("occurredAt", LocalDateTime.now().toString());
        appendOutbox(
                "ProductOwnerChanged:" + productId + ":" + newAssigneeId,
                ProductDomainEventTypes.PRODUCT_OWNER_CHANGED,
                OutboxEventAppender.AGGREGATE_PRODUCT,
                productId,
                payload,
                operatorId);
    }

    public void publishActivitySyncCompleted(String activityId, int syncedProductCount) {
        publishActivitySyncCompleted(
                activityId,
                null,
                "FULL",
                0,
                syncedProductCount,
                0,
                "SUCCESS",
                null);
    }

    public void publishActivitySyncCompleted(
            String activityId,
            String activityName,
            String syncType,
            int createdCount,
            int updatedCount,
            int skippedCount,
            String syncStatus,
            UUID operatorId) {
        LocalDateTime occurredAt = LocalDateTime.now();
        ActivitySyncCompletedEvent event = new ActivitySyncCompletedEvent(
                UUID.randomUUID().toString(),
                activityId,
                activityName,
                syncType,
                createdCount,
                updatedCount,
                skippedCount,
                syncStatus,
                operatorId,
                occurredAt,
                null);
        publishSpringEvent(event);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", activityId);
        payload.put("activityName", activityName);
        payload.put("syncType", syncType);
        payload.put("createdCount", createdCount);
        payload.put("updatedCount", updatedCount);
        payload.put("skippedCount", skippedCount);
        payload.put("syncStatus", syncStatus);
        payload.put("operatorId", operatorId == null ? null : operatorId.toString());
        payload.put("occurredAt", occurredAt.toString());
        appendOutbox(
                "ActivitySyncCompleted:" + activityId + ":" + occurredAt.toLocalDate(),
                ProductDomainEventTypes.ACTIVITY_SYNC_COMPLETED,
                OutboxEventAppender.AGGREGATE_ACTIVITY,
                activityId,
                payload,
                operatorId);
    }

    public void publishPartnerSyncCompleted(int upsertedCount) {
        Map<String, Object> payload = Map.of(
                "upsertedCount", upsertedCount,
                "occurredAt", LocalDateTime.now().toString());
        appendOutbox(
                "PartnerSyncCompleted:" + LocalDateTime.now().toLocalDate(),
                ProductDomainEventTypes.PARTNER_SYNC_COMPLETED,
                OutboxEventAppender.AGGREGATE_PARTNER,
                "ALL",
                payload,
                null);
    }

    public void publishPartnerSyncCompleted(
            String partnerId,
            String partnerName,
            String partnerType,
            String source,
            String syncStatus,
            boolean created,
            boolean updated) {
        LocalDateTime occurredAt = LocalDateTime.now();
        PartnerSyncCompletedEvent event = new PartnerSyncCompletedEvent(
                UUID.randomUUID().toString(),
                partnerId,
                partnerName,
                partnerType,
                source,
                syncStatus,
                created,
                updated,
                occurredAt,
                null);
        publishSpringEvent(event);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerId", partnerId);
        payload.put("partnerName", partnerName);
        payload.put("partnerType", partnerType);
        payload.put("source", source);
        payload.put("syncStatus", syncStatus);
        payload.put("created", created);
        payload.put("updated", updated);
        payload.put("occurredAt", occurredAt.toString());
        appendOutbox(
                "PartnerSyncCompleted:" + partnerId + ":" + occurredAt.toLocalDate(),
                ProductDomainEventTypes.PARTNER_SYNC_COMPLETED,
                OutboxEventAppender.AGGREGATE_PARTNER,
                partnerId,
                payload,
                null);
    }

    public void publishActivityExtended(String activityId, String previousEndTime, String newEndTime) {
        Map<String, Object> payload = Map.of(
                "activityId", activityId,
                "previousEndTime", previousEndTime,
                "newEndTime", newEndTime,
                "occurredAt", LocalDateTime.now().toString());
        appendOutbox(
                "ActivityExtended:" + activityId + ":" + newEndTime,
                ProductDomainEventTypes.ACTIVITY_EXTENDED,
                OutboxEventAppender.AGGREGATE_ACTIVITY,
                activityId,
                payload,
                null);
    }

    public void publishDisplayRuleApplied(
            String productId,
            UUID oldRelationId,
            UUID newRelationId,
            int ruleVersion,
            String operatorType,
            UUID operatorId,
            Map<String, Object> detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId);
        payload.put("oldRelationId", oldRelationId == null ? null : oldRelationId.toString());
        payload.put("newRelationId", newRelationId == null ? null : newRelationId.toString());
        payload.put("ruleVersion", ruleVersion);
        payload.put("operatorType", operatorType);
        payload.put("operatorId", operatorId == null ? null : operatorId.toString());
        payload.put("detail", detail);
        payload.put("occurredAt", LocalDateTime.now().toString());
        appendOutbox(
                "ProductDisplayRuleApplied:" + productId + ":" + newRelationId + ":" + ruleVersion,
                ProductDomainEventTypes.PRODUCT_DISPLAY_RULE_APPLIED,
                OutboxEventAppender.AGGREGATE_PRODUCT,
                productId,
                payload,
                operatorId);
    }

    public void publishForceDisplayChanged(
            UUID relationId,
            String productId,
            boolean forceDisplay,
            UUID adminId,
            String reason,
            LocalDateTime until) {
        Map<String, Object> payload = Map.of(
                "relationId", relationId.toString(),
                "productId", productId,
                "forceDisplay", forceDisplay,
                "adminId", adminId == null ? null : adminId.toString(),
                "reason", reason,
                "until", until == null ? null : until.toString(),
                "occurredAt", LocalDateTime.now().toString());
        appendOutbox(
                "ProductForceDisplayChanged:" + relationId + ":" + forceDisplay,
                ProductDomainEventTypes.PRODUCT_FORCE_DISPLAY_CHANGED,
                OutboxEventAppender.AGGREGATE_PRODUCT,
                productId,
                payload,
                adminId);
    }

    /** 由 Outbox 分发器调用，将 Outbox 载荷转为 Spring 本地事件供既有监听器消费。 */
    public void republishSpringEvent(String eventType, String payloadJson) {
        try {
            applicationEventPublisher.publishEvent(Map.of("eventType", eventType, "payload", payloadJson));
        } catch (Exception ex) {
            log.warn("Spring republish failed for eventType={}", eventType, ex);
        }
    }

    private Map<String, Object> basePayload(String activityId, String productId, UUID operationStateId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", activityId);
        payload.put("productId", productId);
        payload.put("relationId", operationStateId == null ? null : operationStateId.toString());
        payload.put("occurredAt", LocalDateTime.now().toString());
        return payload;
    }

    private void publishSpringEvent(Object event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Spring local event publish failed: eventClass={}", event.getClass().getSimpleName(), ex);
        }
    }

    private void appendOutbox(
            String eventKey,
            String eventType,
            String aggregateType,
            String aggregateId,
            Map<String, Object> payload,
            UUID operatorId) {
        try {
            outboxEventAppender.appendIfAbsent(
                    eventKey,
                    eventType,
                    aggregateType,
                    aggregateId,
                    EVENT_VERSION,
                    payload,
                    operatorId,
                    null);
        } catch (Exception ex) {
            log.warn("Outbox append failed: eventType={}, aggregateId={}", eventType, aggregateId, ex);
        }
    }
}
