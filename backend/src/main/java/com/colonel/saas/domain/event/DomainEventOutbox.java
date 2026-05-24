package com.colonel.saas.domain.event;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName(value = "domain_event_outbox", autoResultMap = true)
public class DomainEventOutbox {

    @TableId(value = "event_id", type = IdType.INPUT)
    private UUID eventId;

    private String eventType;

    private String aggregateType;

    private String aggregateId;

    private Integer eventVersion;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payload;

    private String status;

    private Integer retryCount;

    private String errorMessage;

    private LocalDateTime occurredAt;

    private LocalDateTime publishedAt;

    private String createdBy;

    private String traceId;

    @TableField("event_key")
    private String eventKey;

    @TableField(value = "headers", typeHandler = JsonbTypeHandler.class)
    private String headers;

    @TableField("max_retry")
    private Integer maxRetry;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;
}
