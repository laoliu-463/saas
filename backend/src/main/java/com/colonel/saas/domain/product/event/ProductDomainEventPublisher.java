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

/**
 * 商品域事件发布器，负责将商品域业务操作产生的领域事件写入 Outbox 表，
 * 同时通过 Spring {@link ApplicationEventPublisher} 发布本地事件供同步监听器消费。
 *
 * <p>采用 <b>双通道发布</b> 策略：
 * <ol>
 *   <li>Outbox 表 —— 通过 {@link OutboxEventAppender#appendIfAbsent} 写入，
 *       由后台调度器异步分发，保证最终一致性；</li>
 *   <li>Spring 本地事件 —— 通过 {@code ApplicationEventPublisher.publishEvent} 发布，
 *       供同进程内的监听器立即消费（如缓存刷新、审计日志等）。</li>
 * </ol>
 *
 * <p>支持的商品域事件类型包括：商品上架、商品下架、负责人变更、活动同步完成、
 * 合作方同步完成、活动延期、展示规则应用、强制展示变更。</p>
 *
 * <p>所有 {@code appendOutbox} 调用均使用 {@code eventKey} 做幂等去重，
 * 防止同一业务操作重复写入 Outbox。</p>
 */
@Service
public class ProductDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductDomainEventPublisher.class);

    /** 事件协议版本号，用于消费端做版本兼容处理。 */
    private static final int EVENT_VERSION = 1;

    /** Outbox 幂等写入器，负责事件去重和持久化。 */
    private final OutboxEventAppender outboxEventAppender;

    /** Spring 本地事件发布器，用于同步通知同进程监听器。 */
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 构造函数，注入 Outbox 写入器和 Spring 事件发布器。
     *
     * @param outboxEventAppender      Outbox 幂等写入器
     * @param applicationEventPublisher Spring 应用事件发布器
     */
    public ProductDomainEventPublisher(
            OutboxEventAppender outboxEventAppender,
            ApplicationEventPublisher applicationEventPublisher) {
        this.outboxEventAppender = outboxEventAppender;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 发布商品上架事件。
     *
     * <p>当商品加入商品库并参与展示竞争时触发。事件同时写入 Outbox 和 Spring 本地事件。</p>
     *
     * @param activityId         活动 ID
     * @param productId          商品 ID（聚合根 ID）
     * @param operationStateId   操作状态记录 ID（用于幂等去重）
     * @param operatorId         操作人 ID
     * @param displayRuleVersion 展示规则版本号
     * @param displayReason      上架展示原因
     */
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

    /**
     * 发布商品下架（隐藏）事件。
     *
     * <p>当商品从商品库展示中移除时触发。可能原因包括：活动结束、规则淘汰、手动下架等。</p>
     *
     * @param activityId         活动 ID
     * @param productId          商品 ID（聚合根 ID）
     * @param operationStateId   操作状态记录 ID
     * @param reason             下架原因
     * @param displayRuleVersion 展示规则版本号
     */
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

    /**
     * 发布商品负责人变更事件。
     *
     * <p>当商品的运营负责人（assignee）发生变更时触发，如转派、重新分配等。</p>
     *
     * @param activityId    活动 ID
     * @param productId     商品 ID（聚合根 ID）
     * @param oldAssigneeId 原负责人 ID（可为 null，表示之前无负责人）
     * @param newAssigneeId 新负责人 ID（可为 null，表示取消负责人）
     * @param operatorId    操作人 ID
     */
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

    /**
     * 发布活动同步完成事件（简化版本）。
     *
     * <p>适用于全量同步场景，仅记录同步的商品总数。默认同步类型为 FULL，状态为 SUCCESS。</p>
     *
     * @param activityId          活动 ID
     * @param syncedProductCount  同步的商品数量
     */
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

    /**
     * 发布活动同步完成事件（完整版本）。
     *
     * <p>当抖音活动商品数据同步完成时触发，包含同步类型、创建/更新/跳过计数和最终状态。
     * 同时通过 Spring 本地事件通知同步监听器（如更新活动缓存）。</p>
     *
     * @param activityId    活动 ID
     * @param activityName  活动名称（可为 null）
     * @param syncType      同步类型（如 FULL / INCREMENTAL）
     * @param createdCount  新创建的商品数量
     * @param updatedCount  更新的商品数量
     * @param skippedCount  跳过的商品数量
     * @param syncStatus    同步状态（如 SUCCESS / PARTIAL / FAILED）
     * @param operatorId    操作人 ID（可为 null，表示系统自动同步）
     */
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

    /**
     * 发布合作方同步完成事件（简化版本，仅记录变更总数）。
     *
     * <p>适用于批量同步场景，聚合根 ID 固定为 {@code "ALL"}，
     * eventKey 按日期去重，同一天多次同步仅保留最新一条。</p>
     *
     * @param upsertedCount 合作方新增/更新总数
     */
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

    /**
     * 发布合作方同步完成事件（完整版本，单个合作方粒度）。
     *
     * <p>当单个合作方数据同步完成时触发，同时通过 Spring 本地事件通知同步监听器。
     * 记录合作方基本信息、来源、同步状态以及是否为新建或更新。</p>
     *
     * @param partnerId    合作方 ID
     * @param partnerName  合作方名称
     * @param partnerType  合作方类型（如达人、商家等）
     * @param source       数据来源
     * @param syncStatus   同步状态（如 SUCCESS / FAILED）
     * @param created      是否为新建记录
     * @param updated      是否为更新记录
     */
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

    /**
     * 发布活动延期事件。
     *
     * <p>当活动结束时间被延长时触发，记录延期前后的结束时间。</p>
     *
     * @param activityId      活动 ID
     * @param previousEndTime 延期前的结束时间（ISO-8601 字符串）
     * @param newEndTime      延期后的结束时间（ISO-8601 字符串）
     */
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

    /**
     * 发布展示规则应用事件。
     *
     * <p>当系统对某个商品应用展示规则（如排名算法决定谁展示、谁隐藏）时触发。
     * 记录规则版本、操作类型和详细信息，用于展示决策的审计追踪。</p>
     *
     * @param productId     商品 ID（聚合根 ID）
     * @param oldRelationId 原关联 ID（可为 null）
     * @param newRelationId 新关联 ID
     * @param ruleVersion   规则版本号
     * @param operatorType  操作类型（如 SYSTEM、MANUAL）
     * @param operatorId    操作人 ID（可为 null）
     * @param detail        规则应用详情（键值对形式）
     */
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

    /**
     * 发布强制展示变更事件。
     *
     * <p>当管理员手动设置或取消商品的强制展示状态时触发。
     * 强制展示可以覆盖展示规则，确保特定商品始终展示或隐藏。</p>
     *
     * @param relationId    商品关联 ID
     * @param productId     商品 ID（聚合根 ID）
     * @param forceDisplay  是否强制展示（true = 强制展示，false = 取消强制展示）
     * @param adminId       管理员 ID（可为 null）
     * @param reason        变更原因
     * @param until         强制展示截止时间（可为 null，表示永久）
     */
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

    /**
     * 发布商品转链完成事件。
     *
     * <p>该事件表示本地推广链接与归因映射事实已经写入，可被订单归因和审计链路消费。</p>
     */
    public void publishPromotionLinkCompleted(
            String activityId,
            String productId,
            UUID promotionLinkId,
            UUID mappingId,
            UUID operatorId,
            String talentId,
            String pickSource,
            String pickExtra,
            String promoteLink,
            String shortLink,
            String scene) {
        LocalDateTime occurredAt = LocalDateTime.now();
        ProductPromotionLinkCompletedEvent event = new ProductPromotionLinkCompletedEvent(
                UUID.randomUUID(),
                activityId,
                productId,
                promotionLinkId,
                mappingId,
                operatorId,
                talentId,
                pickSource,
                pickExtra,
                promoteLink,
                shortLink,
                scene,
                occurredAt,
                null);
        publishSpringEvent(event);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", activityId);
        payload.put("productId", productId);
        payload.put("promotionLinkId", promotionLinkId == null ? null : promotionLinkId.toString());
        payload.put("mappingId", mappingId == null ? null : mappingId.toString());
        payload.put("operatorId", operatorId == null ? null : operatorId.toString());
        payload.put("talentId", talentId);
        payload.put("pickSource", pickSource);
        payload.put("pickExtra", pickExtra);
        payload.put("promoteLink", promoteLink);
        payload.put("shortLink", shortLink);
        payload.put("scene", scene);
        payload.put("occurredAt", occurredAt.toString());
        appendOutbox(
                "ProductPromotionLinkCompleted:" + promotionLinkId,
                ProductDomainEventTypes.PRODUCT_PROMOTION_LINK_COMPLETED,
                OutboxEventAppender.AGGREGATE_PRODUCT,
                productId,
                payload,
                operatorId);
    }

    /** 由 Outbox 分发器调用，将 Outbox 载荷转为 Spring 本地事件供既有监听器消费。 */
    public void republishSpringEvent(String eventType, String payloadJson) {
        try {
            applicationEventPublisher.publishEvent(Map.of("eventType", eventType, "payload", payloadJson));
        } catch (Exception ex) {
            log.warn("Spring republish failed for eventType={}", eventType, ex);
        }
    }

    /**
     * 构造商品事件的基础载荷（activityId、productId、relationId、occurredAt）。
     *
     * @param activityId       活动 ID
     * @param productId        商品 ID
     * @param operationStateId 操作状态记录 ID（映射为 relationId）
     * @return 基础载荷 Map，调用方可继续追加额外字段
     */
    private Map<String, Object> basePayload(String activityId, String productId, UUID operationStateId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", activityId);
        payload.put("productId", productId);
        payload.put("relationId", operationStateId == null ? null : operationStateId.toString());
        payload.put("occurredAt", LocalDateTime.now().toString());
        return payload;
    }

    /**
     * 通过 Spring 事件发布器发布本地事件，失败仅记录警告日志不影响主流程。
     *
     * @param event 事件对象（如 {@link ActivitySyncCompletedEvent}）
     */
    private void publishSpringEvent(Object event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Spring local event publish failed: eventClass={}", event.getClass().getSimpleName(), ex);
        }
    }

    /**
     * 将事件写入 Outbox 表，失败仅记录警告日志不影响主流程。
     *
     * <p>使用 {@link OutboxEventAppender#appendIfAbsent} 做幂等去重，
     * 相同 eventKey 的事件不会重复写入。</p>
     *
     * @param eventKey     事件幂等键（用于去重）
     * @param eventType    事件类型标识
     * @param aggregateType 聚合类型（如 PRODUCT、ACTIVITY、PARTNER）
     * @param aggregateId  聚合根 ID
     * @param payload      事件载荷（键值对，将序列化为 JSON 存储）
     * @param operatorId   操作人 ID（可为 null）
     */
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
