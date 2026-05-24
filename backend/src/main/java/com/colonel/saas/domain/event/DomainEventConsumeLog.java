package com.colonel.saas.domain.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("domain_event_consume_log")
public class DomainEventConsumeLog {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID eventId;

    private String consumerName;

    private String status;

    private String errorMessage;

    private LocalDateTime consumedAt;

    private LocalDateTime createdAt;
}
