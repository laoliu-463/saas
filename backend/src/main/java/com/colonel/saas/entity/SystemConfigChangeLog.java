package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("system_config_change_log")
public class SystemConfigChangeLog {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID configId;

    private String configKey;

    private String changeAction;

    private String oldValue;

    private String newValue;

    private String source;

    private UUID operatorId;

    private LocalDateTime changedAt;

    private UUID eventId;

    private String changeReason;

    private Integer configVersion;
}
