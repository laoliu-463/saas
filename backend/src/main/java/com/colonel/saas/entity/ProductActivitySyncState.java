package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 活动级商品同步 checkpoint 状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_activity_sync_state")
public class ProductActivitySyncState extends BaseEntity {

    @TableField("activity_id")
    private String activityId;

    @TableField("scope")
    private String scope;

    @TableField("last_success_at")
    private LocalDateTime lastSuccessAt;

    @TableField("last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @TableField("last_status")
    private String lastStatus;

    @TableField("last_stop_reason")
    private String lastStopReason;

    @TableField("last_cursor")
    private String lastCursor;

    @TableField("last_page")
    private Integer lastPage;

    @TableField("last_fetched_rows")
    private Long lastFetchedRows;

    @TableField("last_distinct_product_ids")
    private Long lastDistinctProductIds;

    @TableField("last_inserted")
    private Integer lastInserted;

    @TableField("last_updated")
    private Integer lastUpdated;

    @TableField("last_skipped")
    private Integer lastSkipped;

    @TableField("last_failed")
    private Integer lastFailed;

    @TableField("consecutive_failures")
    private Integer consecutiveFailures;

    @TableField("last_error_message")
    private String lastErrorMessage;
}
