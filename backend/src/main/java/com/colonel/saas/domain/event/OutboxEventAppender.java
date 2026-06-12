package com.colonel.saas.domain.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.event.DomainEventStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox 事件追加器，提供幂等写入能力。
 *
 * <p>当业务代码需要在事务中写入领域事件时，通过本服务的 {@link #appendIfAbsent} 方法
 * 先按 eventKey 查重，已存在则直接返回已有 eventId（幂等），不存在则插入新记录。
 * 这保证了同一业务操作重试时不会产生重复事件。</p>
 *
 * <p>本类由各域 Publisher（如 {@link com.colonel.saas.domain.product.event.ProductDomainEventPublisher}、
 * {@link com.colonel.saas.domain.sample.event.SampleDomainEventPublisher}）调用，
 * 是 Outbox 模式的统一写入入口。</p>
 */
@Service
public class OutboxEventAppender {

    /** 聚合根类型常量：商品域。 */
    public static final String AGGREGATE_PRODUCT = "PRODUCT";

    /** 聚合根类型常量：活动域。 */
    public static final String AGGREGATE_ACTIVITY = "ACTIVITY";

    /** 聚合根类型常量：合作方域。 */
    public static final String AGGREGATE_PARTNER = "PARTNER";

    /** 聚合根类型常量：寄样域。 */
    public static final String AGGREGATE_SAMPLE = "SAMPLE";

    /** 聚合根类型常量：订单域。 */
    public static final String AGGREGATE_ORDER = "ORDER";

    private final DomainEventOutboxMapper domainEventOutboxMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，注入 Outbox Mapper 和 JSON 序列化器。
     *
     * @param domainEventOutboxMapper Outbox 表数据访问接口
     * @param objectMapper            Jackson JSON 序列化器
     */
    public OutboxEventAppender(DomainEventOutboxMapper domainEventOutboxMapper, ObjectMapper objectMapper) {
        this.domainEventOutboxMapper = domainEventOutboxMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 幂等追加事件到 Outbox 表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若 eventKey 非空，先查询是否已存在相同 eventKey 的事件</li>
     *   <li>已存在则直接返回已有事件的 eventId（幂等保证）</li>
     *   <li>不存在则生成新的 UUID，构建 {@link DomainEventOutbox} 实体并插入</li>
     * </ol>
     * </p>
     *
     * @param eventKey      事件幂等键（可为 null，此时不做去重检查）
     * @param eventType     事件类型标识符，如 {@code PRODUCT_LISTED}
     * @param aggregateType 聚合根类型，如 {@code PRODUCT}、{@code SAMPLE}
     * @param aggregateId   聚合根 ID（业务实体主键）
     * @param eventVersion  事件载荷版本号
     * @param payload       事件载荷对象（将被序列化为 JSON）
     * @param operatorId    操作人 ID（可为 null）
     * @param headers       事件附加头信息（可为 null）
     * @return 事件 ID（新生成的或已存在的）
     */
    public UUID appendIfAbsent(
            String eventKey,
            String eventType,
            String aggregateType,
            String aggregateId,
            int eventVersion,
            Object payload,
            UUID operatorId,
            Map<String, Object> headers) {
        if (eventKey != null) {
            DomainEventOutbox existing = domainEventOutboxMapper.selectOne(new LambdaQueryWrapper<DomainEventOutbox>()
                    .eq(DomainEventOutbox::getEventKey, eventKey)
                    .last("LIMIT 1"));
            if (existing != null) {
                return existing.getEventId();
            }
        }
        UUID eventId = UUID.randomUUID();
        DomainEventOutbox outbox = new DomainEventOutbox();
        outbox.setEventId(eventId);
        outbox.setEventKey(eventKey);
        outbox.setEventType(eventType);
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventVersion(eventVersion);
        outbox.setPayload(serialize(payload));
        outbox.setHeaders(headers == null ? null : serialize(headers));
        outbox.setStatus(DomainEventStatus.PENDING.name());
        outbox.setRetryCount(0);
        outbox.setMaxRetry(5);
        outbox.setNextRetryAt(LocalDateTime.now());
        outbox.setOccurredAt(LocalDateTime.now());
        outbox.setCreatedBy(operatorId == null ? null : operatorId.toString());
        domainEventOutboxMapper.insert(outbox);
        return eventId;
    }

    /**
     * 将任意对象序列化为 JSON 字符串。
     *
     * @param payload 待序列化对象（通常为 Map 或 record）
     * @return JSON 字符串
     * @throws IllegalStateException 当序列化失败时抛出
     */
    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
