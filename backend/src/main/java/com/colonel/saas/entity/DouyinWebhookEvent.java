package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 抖音 Webhook 事件实体。
 * <p>
 * 对应数据库表：{@code douyin_webhook_event}，记录从抖音平台接收到的所有 Webhook 推送事件。
 * 采用"先落库、后消费"的架构模式：事件到达时先持久化原始载荷，再由消费者异步处理。
 * 通过 payloadHash 进行幂等去重，支持重试机制，确保事件不丢失、不重复消费。
 * 继承 {@link com.colonel.saas.common.base.BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("douyin_webhook_event")
public class DouyinWebhookEvent extends BaseEntity {

    /**
     * 事件唯一标识
     * <p>对应数据库列：{@code event_key}，抖音平台推送的事件唯一标识，
     * 用于事件级别幂等判断和去重</p>
     */
    @TableField("event_key")
    private String eventKey;

    /**
     * 事件类型
     * <p>对应数据库列：{@code event_type}，标识推送事件的业务类型，
     * 如订单状态变更、商品审核结果、达人资料更新等</p>
     */
    @TableField("event_type")
    private String eventType;

    /**
     * 载荷哈希
     * <p>对应数据库列：{@code payload_hash}，对原始载荷进行哈希计算后的值，
     * 用于幂等去重：相同 hash 的事件不会被重复处理</p>
     */
    @TableField("payload_hash")
    private String payloadHash;

    /**
     * 请求体长度
     * <p>对应数据库列：{@code body_length}，原始 Webhook 请求体的字节长度，
     * 用于监控和异常检测</p>
     */
    @TableField("body_length")
    private Integer bodyLength;

    /**
     * 原始载荷
     * <p>对应数据库列：{@code raw_payload}，抖音平台推送的原始 JSON 请求体，
     * 完整保留以便问题排查和数据回放</p>
     */
    @TableField("raw_payload")
    private String rawPayload;

    /**
     * 处理状态
     * <p>对应数据库列：{@code status}，事件的消费处理状态，
     * 如 "PENDING"（待处理）、"PROCESSING"（处理中）、"SUCCESS"（处理成功）、
     * "FAILED"（处理失败）等</p>
     */
    @TableField("status")
    private String status;

    /**
     * 消费结果
     * <p>对应数据库列：{@code consume_result}，事件消费处理的结果描述，
     * 成功时记录处理摘要，失败时记录错误原因</p>
     */
    @TableField("consume_result")
    private String consumeResult;

    /**
     * 重试次数
     * <p>对应数据库列：{@code retry_count}，事件处理失败后的重试次数累计，
     * 达到最大重试次数后标记为最终失败</p>
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 接收时间
     * <p>对应数据库列：{@code received_at}，Webhook 事件到达系统的时间</p>
     */
    @TableField("received_at")
    private LocalDateTime receivedAt;

    /**
     * 处理完成时间
     * <p>对应数据库列：{@code processed_at}，事件被消费者成功处理的时间，
     * 未处理时为 null</p>
     */
    @TableField("processed_at")
    private LocalDateTime processedAt;
}
