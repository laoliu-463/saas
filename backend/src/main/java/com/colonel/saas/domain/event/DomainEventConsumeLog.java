package com.colonel.saas.domain.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 领域事件消费日志实体，记录每个消费者对每个事件的消费结果。
 *
 * <p>配合 Outbox 模式实现消费端幂等性保障：
 * 同一事件对同一消费者只消费一次（通过 {@link DomainEventConsumeLogMapper#findSuccessful} 查询判断）。</p>
 *
 * <p>对应数据库表：{@code domain_event_consume_log}。</p>
 */
@Data
@TableName("domain_event_consume_log")
public class DomainEventConsumeLog {

    /** 消费日志唯一标识（UUID），由应用层生成。 */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /** 被消费的事件 ID，关联 {@link DomainEventOutbox#eventId}。 */
    private UUID eventId;

    /**
     * 消费者名称，标识哪个消费者处理了此事件。
     * 例如：{@code product-config-consumer}、{@code performance-config-consumer}。
     */
    private String consumerName;

    /**
     * 消费结果状态，取值为 {@code DomainEventOutboxService.CONSUME_SUCCESS}
     * 或 {@code DomainEventOutboxService.CONSUME_FAILED}。
     */
    private String status;

    /** 消费失败时的错误信息，成功时为 null。 */
    private String errorMessage;

    /** 事件消费完成的时间点。 */
    private LocalDateTime consumedAt;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;
}
