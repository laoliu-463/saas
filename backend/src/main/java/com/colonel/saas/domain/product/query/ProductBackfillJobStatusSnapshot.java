package com.colonel.saas.domain.product.query;

import java.time.LocalDateTime;

/**
 * 活动商品 backfill job log 的读侧快照。
 */
public record ProductBackfillJobStatusSnapshot(
        String jobId,
        String status,
        Boolean dryRun,
        String scope,
        Integer activitiesScanned,
        Integer activitiesSuccess,
        Integer activitiesIncomplete,
        Integer activitiesFailed,
        Long apiFetchedRows,
        Long apiDistinctProductIds,
        Integer inserted,
        Integer updated,
        Integer skipped,
        Integer failed,
        String requestParamsJson,
        String stopReasonStatsJson,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
}
