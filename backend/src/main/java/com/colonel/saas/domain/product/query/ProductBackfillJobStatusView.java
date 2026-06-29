package com.colonel.saas.domain.product.query;

import java.util.Map;

/**
 * 活动商品 backfill job 状态读模型。
 */
public record ProductBackfillJobStatusView(
        String jobId,
        String status,
        boolean dryRun,
        String scope,
        int activitiesScanned,
        int activitiesSuccess,
        int activitiesIncomplete,
        int activitiesFailed,
        long apiFetchedRows,
        long apiDistinctProductIds,
        long dbRowsBefore,
        long estimatedGapRows,
        int inserted,
        int updated,
        int skipped,
        int failed,
        Map<String, Long> stopReasonStats,
        String currentActivityId,
        String lastProgressAt,
        long lockWaitCount,
        long deadlockRetryCount,
        int unchanged,
        String startedAt,
        String finishedAt) {
}
