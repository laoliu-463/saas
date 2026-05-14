package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("douyin_webhook_event")
public class DouyinWebhookEvent extends BaseEntity {

    @TableField("event_key")
    private String eventKey;

    @TableField("event_type")
    private String eventType;

    @TableField("payload_hash")
    private String payloadHash;

    @TableField("body_length")
    private Integer bodyLength;

    @TableField("raw_payload")
    private String rawPayload;

    @TableField("status")
    private String status;

    @TableField("consume_result")
    private String consumeResult;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("received_at")
    private LocalDateTime receivedAt;

    @TableField("processed_at")
    private LocalDateTime processedAt;
}
