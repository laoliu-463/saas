package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_enrich_task")
public class TalentEnrichTask extends BaseEntity {

    @TableField("talent_id")
    private UUID talentId;

    @TableField("input_value")
    private String inputValue;

    @TableField("input_type")
    private String inputType;

    @TableField("source_type")
    private String sourceType;

    @TableField("task_status")
    private String taskStatus;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    @TableField("error_msg")
    private String errorMsg;
}

