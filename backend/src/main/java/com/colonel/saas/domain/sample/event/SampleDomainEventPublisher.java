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

/**
 * 寄样域事件发布器，负责将寄样流程中各阶段产生的领域事件写入 Outbox 表，
 * 同时通过 Spring {@link ApplicationEventPublisher} 发布本地事件供同步监听器消费。
 *
 * <p>采用 <b>双通道发布</b> 策略：
 * <ol>
 *   <li>Outbox 表 —— 通过 {@link OutboxEventAppender#appendIfAbsent} 写入，
 *       由后台调度器异步分发，保证最终一致性；</li>
 *   <li>Spring 本地事件 —— 通过 {@code ApplicationEventPublisher.publishEvent} 发布，
 *       供同进程内的监听器立即消费（如库存更新、审计日志等）。</li>
 * </ol>
 *
 * <p>支持的寄样域事件类型：创建、审批通过、审批拒绝、发货、签收、完成（交作业）、关闭。
 * 覆盖寄样请求的完整生命周期。</p>
 *
 * <p>所有发布方法均对 {@code sample} 参数做 null 安全检查，
 * 当寄样请求无效时静默返回不抛异常。所有 {@code appendOutbox} 调用使用
 * {@code eventKey} 做幂等去重。</p>
 */
@Service
public class SampleDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SampleDomainEventPublisher.class);

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
    public SampleDomainEventPublisher(
            OutboxEventAppender outboxEventAppender,
            ApplicationEventPublisher applicationEventPublisher) {
        this.outboxEventAppender = outboxEventAppender;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 发布寄样请求创建事件。
     *
     * <p>当新的寄样请求创建成功后触发，记录商品、达人、渠道、招募人等完整关联信息。
     * 同时通过 Spring 本地事件通知同步监听器。</p>
     *
     * @param sample      寄样请求实体（不可为 null，且必须有 ID）
     * @param productName 商品名称
     * @param channelName 渠道名称
     * @param recruiterId 招募人 ID（可为 null）
     * @param partnerId   合作方 ID（可为 null）
     */
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

    /**
     * 发布寄样审批通过事件。
     *
     * <p>当寄样请求被审批通过时触发，记录审批人和审批时间。</p>
     *
     * @param sample     寄样请求实体
     * @param recruiterId 招募人 ID（可为 null）
     * @param approvedBy 审批人 ID
     * @param approvedAt 审批时间（可为 null，默认使用当前时间）
     */
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

    /**
     * 发布寄样审批拒绝事件。
     *
     * <p>当寄样请求被审批拒绝时触发，记录拒绝人、拒绝原因和拒绝时间。</p>
     *
     * @param sample     寄样请求实体
     * @param rejectedBy 拒绝人 ID
     * @param reason     拒绝原因
     * @param rejectedAt 拒绝时间（可为 null，默认使用当前时间）
     */
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

    /**
     * 发布寄样发货事件。
     *
     * <p>当寄样请求的样品发出时触发，记录物流信息（物流公司和快递单号）。
     * 要求寄样请求必须有快递单号（{@code trackingNo}），否则静默返回。</p>
     *
     * @param sample    寄样请求实体（必须有 trackingNo）
     * @param shippedBy 发货人 ID
     * @param shippedAt 发货时间（可为 null，默认使用当前时间）
     */
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

    /**
     * 发布寄样签收事件。
     *
     * <p>当达人确认签收样品时触发。签收后达人可以开始交作业（发布带货内容）。</p>
     *
     * @param sample   寄样请求实体
     * @param signedAt 签收时间（可为 null，默认使用当前时间）
     */
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

    /**
     * 发布寄样完成事件（交作业完成）。
     *
     * <p>当达人通过订单已同步事件判断交作业完成时触发。
     * 记录关联的订单 ID，用于业绩归属和提成计算。</p>
     *
     * @param sample      寄样请求实体
     * @param orderId     关联的订单 ID
     * @param completedAt 完成时间（可为 null，默认使用当前时间）
     */
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

    /**
     * 发布寄样关闭事件。
     *
     * <p>当寄样请求因超时、取消等原因关闭时触发，记录关闭原因。</p>
     *
     * @param sample     寄样请求实体
     * @param closeReason 关闭原因
     * @param closedAt   关闭时间（可为 null，默认使用当前时间）
     */
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

    /**
     * 将事件写入 Outbox 表，失败仅记录警告日志不影响主流程。
     *
     * <p>使用 {@link OutboxEventAppender#appendIfAbsent} 做幂等去重。
     * 聚合类型固定为 {@link OutboxEventAppender#AGGREGATE_SAMPLE}。</p>
     *
     * @param eventKey       事件幂等键（用于去重）
     * @param eventType      事件类型标识
     * @param sampleRequestId 寄样请求 ID（作为聚合根 ID）
     * @param payload        事件载荷
     * @param operatorId     操作人 ID（可为 null）
     */
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

    /**
     * 通过 Spring 事件发布器发布本地事件，失败仅记录警告日志不影响主流程。
     *
     * @param event 事件对象（如 {@link SampleCreatedEvent}）
     */
    private void publishSpring(Object event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Spring local event publish failed: eventClass={}", event.getClass().getSimpleName(), ex);
        }
    }

    /**
     * 由 Outbox 路由器调用，将 Outbox 载荷转为 Spring 本地事件供既有监听器消费。
     *
     * <p>发布一个包含 {@code eventType} 和 {@code payload} 键的 Map 事件，
     * 监听器可通过 eventType 判断如何处理。</p>
     *
     * @param eventType   事件类型标识
     * @param payloadJson 事件载荷 JSON 字符串
     */
    public void republishSpringEvent(String eventType, String payloadJson) {
        try {
            applicationEventPublisher.publishEvent(Map.of("eventType", eventType, "payload", payloadJson));
        } catch (Exception ex) {
            log.warn("Spring republish failed for eventType={}", eventType, ex);
        }
    }

    /**
     * 从键值对参数构造有序 Map。
     *
     * <p>接受交替的 key-value 参数，如 {@code mapOf("a", 1, "b", 2)}。
     * 使用 {@link LinkedHashMap} 保证插入顺序。</p>
     *
     * @param kv 交替排列的键值对参数
     * @return 保持插入顺序的 Map
     */
    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    /**
     * 将 UUID 转为字符串，null 安全。
     *
     * @param id UUID 对象（可为 null）
     * @return UUID 字符串形式，null 输入返回 null
     */
    private static String uuid(UUID id) {
        return id == null ? null : id.toString();
    }
}
