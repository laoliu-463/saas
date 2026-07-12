package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 活动列表异步同步任务日志实体。
 * <p>
 * 对应数据库表：{@code colonel_activity_sync_job_log}，记录活动列表/状态同步的异步任务状态。
 * 时间语义独立于商品同步的 {@code product_sync_job_log}。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "colonel_activity_sync_job_log", autoResultMap = true)
public class ColonelActivitySyncJobLog extends BaseEntity {

    @TableField("job_id")
    private String jobId;

    /** 同步类型：ACTIVITY_LIST / ACTIVITY_DETAIL */
    @TableField("sync_type")
    private String syncType;

    /** 任务状态：QUEUED / RUNNING / SUCCESS / PARTIAL / FAILED / ABANDONED */
    @TableField("status")
    private String status;

    @TableField("triggered_by")
    private UUID triggeredBy;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("activities_total")
    private Integer activitiesTotal;

    @TableField("activities_synced")
    private Integer activitiesSynced;

    @TableField("activities_failed")
    private Integer activitiesFailed;

    @TableField("error_message")
    private String errorMessage;

    @TableField("metadata_json")
    private String metadataJson;
}
