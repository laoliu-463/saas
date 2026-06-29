package com.colonel.saas.domain.product.application;

import com.colonel.saas.domain.product.query.ProductBackfillJobStatusQueryService;
import com.colonel.saas.domain.product.query.ProductBackfillJobStatusView;
import com.colonel.saas.service.ActivityProductPaginationRunner;
import com.colonel.saas.service.ProductActivityBackfillService;
import com.colonel.saas.service.ProductSyncDryRunProbeService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商品域活动商品 backfill 应用入口。
 *
 * <p>API 层只暴露商品域 command/result；legacy backfill service 保留执行细节。</p>
 */
@Service
public class ProductActivityBackfillApplicationService {

    private final ProductActivityBackfillService backfillService;
    private final ProductBackfillJobStatusQueryService jobStatusQueryService;

    public ProductActivityBackfillApplicationService(
            ProductActivityBackfillService backfillService,
            ProductBackfillJobStatusQueryService jobStatusQueryService) {
        this.backfillService = backfillService;
        this.jobStatusQueryService = jobStatusQueryService;
    }

    public BackfillResult backfill(BackfillCommand command, UUID requestedBy) {
        return BackfillResult.from(backfillService.backfill(toLegacyRequest(command), requestedBy));
    }

    public BackfillAsyncResponse backfillAsync(BackfillCommand command, UUID requestedBy) {
        ProductActivityBackfillService.BackfillAsyncResponse response =
                backfillService.backfillAsync(toLegacyRequest(command), requestedBy);
        return new BackfillAsyncResponse(response.jobId(), response.status());
    }

    public BackfillJobStatus getJobStatus(String jobId) {
        return BackfillJobStatus.from(jobStatusQueryService.getJobStatus(jobId));
    }

    private ProductActivityBackfillService.BackfillRequest toLegacyRequest(BackfillCommand command) {
        BackfillCommand safe = command == null
                ? new BackfillCommand(null, List.of(), null, null, null, null, true, false, null)
                : command;
        return new ProductActivityBackfillService.BackfillRequest(
                safe.scope(),
                safe.activityIds() == null ? List.of() : List.copyOf(safe.activityIds()),
                safe.pageSize(),
                safe.maxActivities(),
                safe.maxPagesPerActivity(),
                safe.maxRowsPerActivity(),
                safe.dryRun(),
                safe.confirm(),
                safe.displayRefreshMode());
    }

    public record BackfillCommand(
            String scope,
            List<String> activityIds,
            Integer pageSize,
            Integer maxActivities,
            Integer maxPagesPerActivity,
            Integer maxRowsPerActivity,
            Boolean dryRun,
            Boolean confirm,
            String displayRefreshMode) {
    }

    public record BackfillResult(
            String jobId,
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
            List<ActivityDryRunSummary> topGapActivities,
            long lockWaitCount,
            long deadlockRetryCount,
            int unchanged) {

        private static BackfillResult from(ProductActivityBackfillService.BackfillResult result) {
            return new BackfillResult(
                    result.jobId(),
                    result.dryRun(),
                    result.scope(),
                    result.activitiesScanned(),
                    result.activitiesSuccess(),
                    result.activitiesIncomplete(),
                    result.activitiesFailed(),
                    result.apiFetchedRows(),
                    result.apiDistinctProductIds(),
                    result.dbRowsBefore(),
                    result.estimatedGapRows(),
                    result.inserted(),
                    result.updated(),
                    result.skipped(),
                    result.failed(),
                    copyStats(result.stopReasonStats()),
                    ActivityDryRunSummary.from(result.topGapActivities()),
                    result.lockWaitCount(),
                    result.deadlockRetryCount(),
                    result.unchanged());
        }
    }

    public record BackfillAsyncResponse(String jobId, String status) {
    }

    public record BackfillJobStatus(
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

        private static BackfillJobStatus from(ProductBackfillJobStatusView status) {
            return new BackfillJobStatus(
                    status.jobId(),
                    status.status(),
                    status.dryRun(),
                    status.scope(),
                    status.activitiesScanned(),
                    status.activitiesSuccess(),
                    status.activitiesIncomplete(),
                    status.activitiesFailed(),
                    status.apiFetchedRows(),
                    status.apiDistinctProductIds(),
                    status.dbRowsBefore(),
                    status.estimatedGapRows(),
                    status.inserted(),
                    status.updated(),
                    status.skipped(),
                    status.failed(),
                    copyStats(status.stopReasonStats()),
                    status.currentActivityId(),
                    status.lastProgressAt(),
                    status.lockWaitCount(),
                    status.deadlockRetryCount(),
                    status.unchanged(),
                    status.startedAt(),
                    status.finishedAt());
        }
    }

    public record ActivityDryRunSummary(
            String activityId,
            boolean dryRun,
            int pageSize,
            int requestedMaxPages,
            int pagesFetched,
            long totalFetchedRows,
            long distinctProductIds,
            long duplicateProductIds,
            long currentDbRowsForActivity,
            long estimatedGapRows,
            long expectedMissingRowsIfCurrentMax100,
            String stoppedReason,
            boolean stillHasNextWhenStopped,
            List<PageSummary> pageSamples,
            List<String> warnings) {

        private static List<ActivityDryRunSummary> from(List<ProductSyncDryRunProbeService.ActivityDryRunResult> results) {
            if (results == null || results.isEmpty()) {
                return List.of();
            }
            return results.stream().map(ActivityDryRunSummary::from).toList();
        }

        private static ActivityDryRunSummary from(ProductSyncDryRunProbeService.ActivityDryRunResult result) {
            return new ActivityDryRunSummary(
                    result.activityId(),
                    result.dryRun(),
                    result.pageSize(),
                    result.requestedMaxPages(),
                    result.pagesFetched(),
                    result.totalFetchedRows(),
                    result.distinctProductIds(),
                    result.duplicateProductIds(),
                    result.currentDbRowsForActivity(),
                    result.estimatedGapRows(),
                    result.expectedMissingRowsIfCurrentMax100(),
                    result.stoppedReason(),
                    result.stillHasNextWhenStopped(),
                    PageSummary.from(result.pageSamples()),
                    result.warnings() == null ? List.of() : List.copyOf(result.warnings()));
        }
    }

    public record PageSummary(
            int pageNo,
            String requestCursor,
            String nextCursor,
            int returned,
            boolean hasNext,
            String firstProductId,
            String lastProductId,
            int duplicateInRun,
            long elapsedMs) {

        private static List<PageSummary> from(List<ActivityProductPaginationRunner.PageSummary> samples) {
            if (samples == null || samples.isEmpty()) {
                return List.of();
            }
            return samples.stream()
                    .map(sample -> new PageSummary(
                            sample.pageNo(),
                            sample.requestCursor(),
                            sample.nextCursor(),
                            sample.returned(),
                            sample.hasNext(),
                            sample.firstProductId(),
                            sample.lastProductId(),
                            sample.duplicateInRun(),
                            sample.elapsedMs()))
                    .toList();
        }
    }

    private static Map<String, Long> copyStats(Map<String, Long> stats) {
        return stats == null || stats.isEmpty() ? Map.of() : Map.copyOf(stats);
    }
}
