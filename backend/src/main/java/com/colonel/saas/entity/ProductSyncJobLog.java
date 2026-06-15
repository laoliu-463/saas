package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品同步任务日志。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sync_job_log")
public class ProductSyncJobLog extends BaseEntity {

    @TableField("job_id")
    private String jobId;

    @TableField("job_type")
    private String jobType;

    @TableField("scope")
    private String scope;

    @TableField("dry_run")
    private Boolean dryRun;

    @TableField("status")
    private String status;

    @TableField("requested_by")
    private UUID requestedBy;

    @TableField("request_params_json")
    private String requestParamsJson;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("activities_scanned")
    private Integer activitiesScanned;

    @TableField("activities_success")
    private Integer activitiesSuccess;

    @TableField("activities_incomplete")
    private Integer activitiesIncomplete;

    @TableField("activities_failed")
    private Integer activitiesFailed;

    @TableField("api_fetched_rows")
    private Long apiFetchedRows;

    @TableField("api_distinct_product_ids")
    private Long apiDistinctProductIds;

    @TableField("inserted")
    private Integer inserted;

    @TableField("updated")
    private Integer updated;

    @TableField("skipped")
    private Integer skipped;

    @TableField("failed")
    private Integer failed;

    @TableField("stop_reason_stats_json")
    private String stopReasonStatsJson;

    @TableField("error_message")
    private String errorMessage;
}
