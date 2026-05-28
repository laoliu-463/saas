package com.colonel.saas.domain.event;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 领域事件 Outbox 表实体，实现 Transactional Outbox 模式的核心持久化对象。
 *
 * <p>当业务操作需要产生领域事件时，事件先以本实体的形式与业务数据在同一事务中
 * 写入 {@code domain_event_outbox} 表，保证"事件不丢失"。
 * 随后由异步派发器（OutboxDispatcher）轮询本表，将 PENDING 状态的事件
 * 投递至下游（Spring 本地事件 / 消息队列），从而实现可靠事件发布。</p>
 *
 * <p>对应数据库表：{@code domain_event_outbox}，
 * 主键为 {@code event_id}（UUID，应用层生成）。</p>
 */
@Data
@TableName(value = "domain_event_outbox", autoResultMap = true)
public class DomainEventOutbox {

    /** 事件唯一标识（UUID），应用层生成，作为表主键。 */
    @TableId(value = "event_id", type = IdType.INPUT)
    private UUID eventId;

    /** 事件类型标识符，例如 {@code PRODUCT_LISTED}、{@code SAMPLE_CREATED}、{@code CONFIG_CHANGED}。 */
    private String eventType;

    /** 聚合根类型，标识事件所属的业务聚合，如 {@code PRODUCT}、{@code SAMPLE}、{@code ACTIVITY}。 */
    private String aggregateType;

    /** 聚合根 ID，即事件关联的业务实体主键（如商品 ID、寄样请求 ID）。 */
    private String aggregateId;

    /** 事件载荷的版本号，用于消费者在载荷结构变更时做兼容性判断。 */
    private Integer eventVersion;

    /**
     * 事件载荷（JSON 格式），存储事件的完整业务数据。
     * 使用 JsonbTypeHandler 映射 PostgreSQL 的 jsonb 列。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payload;

    /**
     * 事件当前状态，取值见 {@link DomainEventStatus}：
     * PENDING / PROCESSING / PUBLISHED / FAILED / DEAD。
     */
    private String status;

    /** 已重试次数，每次投递失败后 +1，超过 maxRetry 则转入 DEAD。 */
    private Integer retryCount;

    /** 最近一次投递失败的错误信息（截断至 2000 字符），PUBLISHED 时清空。 */
    private String errorMessage;

    /** 事件实际发生时间（业务时间），由业务代码在构造事件时设置。 */
    private LocalDateTime occurredAt;

    /** 事件成功发布时间，仅在状态变为 PUBLISHED 时写入。 */
    private LocalDateTime publishedAt;

    /** 事件创建者标识（操作人 ID），用于审计追踪。 */
    private String createdBy;

    /** 链路追踪 ID，关联请求级 traceId，便于跨服务链路排查。 */
    private String traceId;

    /**
     * 事件幂等键，用于 {@link OutboxEventAppender#appendIfAbsent} 去重。
     * 格式由各域 Publisher 自定义，如 {@code ProductListed:{opId}:{version}}。
     */
    @TableField("event_key")
    private String eventKey;

    /**
     * 事件附加头信息（JSON 格式），携带元数据如 traceId、causationId 等。
     * 使用 JsonbTypeHandler 映射 PostgreSQL 的 jsonb 列。
     */
    @TableField(value = "headers", typeHandler = JsonbTypeHandler.class)
    private String headers;

    /** 最大重试次数，默认 5 次，超过后事件进入 DEAD 状态。 */
    @TableField("max_retry")
    private Integer maxRetry;

    /** 下次重试时间点，由指数退避算法计算（base=5s, factor=2^min(retry,6)）。 */
    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;
}
