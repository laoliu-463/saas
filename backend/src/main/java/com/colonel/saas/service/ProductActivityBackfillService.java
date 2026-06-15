package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductActivitySyncState;
import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductActivitySyncStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 活动商品全量回补服务。
 */
@Service
public class ProductActivityBackfillService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_MAX_ACTIVITIES = 50;
    private static final int DEFAULT_MAX_PAGES = 1000;
    private static final int DEFAULT_MAX_ROWS = 50_000;
    private static final int MAX_ACTIVITIES = 200;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProductSyncDryRunProbeService dryRunProbeService;
    private final ProductService productService;
    private final ColonelsettlementActivityMapper activityMapper;
    private final ProductSnapshotMapper snapshotMapper;
    private final ProductSyncJobLogMapper jobLogMapper;
    private final ProductActivitySyncStateMapper syncStateMapper;
    @Value("${product.sync.activityProduct.fullBackfillEnabled:true}")
    private boolean fullBackfillEnabled = true;

    public ProductActivityBackfillService(
            ProductSyncDryRunProbeService dryRunProbeService,
            ProductService productService,
            ColonelsettlementActivityMapper activityMapper,
            ProductSnapshotMapper snapshotMapper,
            ProductSyncJobLogMapper jobLogMapper,
            ProductActivitySyncStateMapper syncStateMapper) {
        this.dryRunProbeService = dryRunProbeService;
        this.productService = productService;
        this.activityMapper = activityMapper;
        this.snapshotMapper = snapshotMapper;
        this.jobLogMapper = jobLogMapper;
        this.syncStateMapper = syncStateMapper;
    }

    public BackfillResult backfill(BackfillRequest request, UUID requestedBy) {
        NormalizedRequest normalized = normalize(request);
        if (!normalized.dryRun() && !fullBackfillEnabled) {
            throw BusinessException.stateInvalid("活动商品 full backfill 已被配置关闭");
        }
        if (!normalized.dryRun() && !normalized.confirm()) {
            throw BusinessException.param("真实 backfill 必须显式 confirm=true");
        }
        String jobId = "product-backfill-" + UUID.randomUUID();
        ProductSyncJobLog jobLog = startJob(jobId, normalized, requestedBy);
        try {
            BackfillResult result = normalized.dryRun()
                    ? runDryRun(jobId, normalized)
                    : runRealBackfill(jobId, normalized);
            finishJob(jobLog, result, statusFromCounts(
                    result.activitiesScanned(),
                    result.activitiesSuccess(),
                    result.activitiesIncomplete(),
                    result.activitiesFailed()), null);
            return result;
        } catch (RuntimeException ex) {
            finishJob(jobLog, failedResult(jobId, normalized), "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private BackfillResult runDryRun(String jobId, NormalizedRequest request) {
        ProductSyncDryRunProbeService.FullDryRunResult dryRun = dryRunProbeService.fullDryRun(
                new ProductSyncDryRunProbeService.FullDryRunRequest(
                        request.scope(),
                        request.activityIds(),
                        request.maxActivities(),
                        request.pageSize(),
                        request.maxPagesPerActivity(),
                        request.maxRowsPerActivity(),
                        true));
        return new BackfillResult(
                jobId,
                true,
                request.scope(),
                dryRun.activitiesScanned(),
                dryRun.activitiesSuccess(),
                dryRun.activitiesIncomplete(),
                dryRun.activitiesFailed(),
                dryRun.apiFetchedRows(),
                dryRun.apiDistinctProductIds(),
                dryRun.dbRowsForScannedActivities(),
                dryRun.estimatedGapRows(),
                0,
                0,
                0,
                dryRun.activitiesFailed(),
                dryRun.stopReasonStats(),
                dryRun.topGapActivities());
    }

    private BackfillResult runRealBackfill(String jobId, NormalizedRequest request) {
        List<String> activityIds = resolveActivityIds(request);
        long dbRowsBefore = activityIds.isEmpty() ? 0L : snapshotMapper.countActiveRowsByActivityIds(activityIds);
        int success = 0;
        int incomplete = 0;
        int failed = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        long fetchedRows = 0L;
        long distinctProductIds = 0L;
        Map<String, Long> stopReasonStats = new LinkedHashMap<>();

        for (String activityId : activityIds) {
            try {
                ProductService.ActivityProductRefreshResult result = productService.refreshActivitySnapshots(
                        buildQueryRequest(activityId, request.pageSize()),
                        request.maxPagesPerActivity(),
                        request.maxRowsPerActivity());
                fetchedRows += result.fetchedRows();
                distinctProductIds += result.distinctProductIds();
                inserted += result.createdCount();
                updated += result.updatedCount();
                skipped += result.skippedCount();
                stopReasonStats.merge(result.stoppedReason(), 1L, Long::sum);
                if (result.complete()) {
                    success++;
                } else if (isFailedStopReason(result.stoppedReason())) {
                    failed++;
                } else {
                    incomplete++;
                }
                upsertActivityState(activityId, request.scope(), result, null);
            } catch (RuntimeException ex) {
                failed++;
                stopReasonStats.merge(ActivityProductPaginationRunner.StopReason.API_ERROR.name(), 1L, Long::sum);
                upsertFailedActivityState(activityId, request.scope(), ex.getMessage());
            }
        }

        return new BackfillResult(
                jobId,
                false,
                request.scope(),
                activityIds.size(),
                success,
                incomplete,
                failed,
                fetchedRows,
                distinctProductIds,
                dbRowsBefore,
                0,
                inserted,
                updated,
                skipped,
                failed,
                stopReasonStats,
                List.of());
    }

    private List<String> resolveActivityIds(NormalizedRequest request) {
        return activityMapper.selectActivityIdsForProductSyncProbe(
                mapperScope(request.scope()),
                request.maxActivities(),
                recentSince(request.scope()),
                request.activityIds());
    }

    private LocalDateTime recentSince(String scope) {
        return switch (scope) {
            case "RECENT_30D" -> LocalDateTime.now().minusDays(30);
            case "RECENT_90D" -> LocalDateTime.now().minusDays(90);
            default -> null;
        };
    }

    private DouyinProductGateway.ActivityProductQueryRequest buildQueryRequest(String activityId, int pageSize) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                null, activityId, 4L, 1L, pageSize, null, null, null, null, 1L, null, null);
    }

    private void upsertActivityState(
            String activityId,
            String scope,
            ProductService.ActivityProductRefreshResult result,
            String errorMessage) {
        ProductActivitySyncState state = new ProductActivitySyncState();
        LocalDateTime now = LocalDateTime.now();
        state.setId(UUID.randomUUID());
        state.setActivityId(activityId);
        state.setScope(scope);
        state.setLastAttemptAt(now);
        state.setLastSuccessAt(result.complete() ? now : null);
        state.setLastStatus(statusForStopReason(result.stoppedReason(), result.complete()));
        state.setLastStopReason(result.stoppedReason());
        state.setLastPage(result.pagesFetched());
        state.setLastFetchedRows((long) result.fetchedRows());
        state.setLastDistinctProductIds((long) result.distinctProductIds());
        state.setLastInserted(result.createdCount());
        state.setLastUpdated(result.updatedCount());
        state.setLastSkipped(result.skippedCount());
        state.setLastFailed(isFailedStopReason(result.stoppedReason()) ? 1 : 0);
        state.setConsecutiveFailures(result.complete() ? 0 : 1);
        state.setLastErrorMessage(errorMessage);
        state.setCreateTime(now);
        state.setUpdateTime(now);
        syncStateMapper.upsert(state);
    }

    private void upsertFailedActivityState(String activityId, String scope, String errorMessage) {
        ProductService.ActivityProductRefreshResult failed = new ProductService.ActivityProductRefreshResult(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                ActivityProductPaginationRunner.StopReason.API_ERROR.name(),
                true,
                false);
        upsertActivityState(activityId, scope, failed, errorMessage);
    }

    private ProductSyncJobLog startJob(String jobId, NormalizedRequest request, UUID requestedBy) {
        ProductSyncJobLog log = new ProductSyncJobLog();
        LocalDateTime now = LocalDateTime.now();
        log.setId(UUID.randomUUID());
        log.setJobId(jobId);
        log.setJobType("sync_activity_product_full_backfill");
        log.setScope(request.scope());
        log.setDryRun(request.dryRun());
        log.setStatus("RUNNING");
        log.setRequestedBy(requestedBy);
        log.setRequestParamsJson(toJson(request));
        log.setStartedAt(now);
        log.setCreateTime(now);
        log.setUpdateTime(now);
        jobLogMapper.insert(log);
        return log;
    }

    private void finishJob(ProductSyncJobLog log, BackfillResult result, String status, String errorMessage) {
        log.setStatus(status);
        log.setFinishedAt(LocalDateTime.now());
        log.setActivitiesScanned(result.activitiesScanned());
        log.setActivitiesSuccess(result.activitiesSuccess());
        log.setActivitiesIncomplete(result.activitiesIncomplete());
        log.setActivitiesFailed(result.activitiesFailed());
        log.setApiFetchedRows(result.apiFetchedRows());
        log.setApiDistinctProductIds(result.apiDistinctProductIds());
        log.setInserted(result.inserted());
        log.setUpdated(result.updated());
        log.setSkipped(result.skipped());
        log.setFailed(result.failed());
        log.setStopReasonStatsJson(toJson(result.stopReasonStats()));
        log.setErrorMessage(errorMessage);
        log.setUpdateTime(LocalDateTime.now());
        jobLogMapper.updateById(log);
    }

    private BackfillResult failedResult(String jobId, NormalizedRequest request) {
        return new BackfillResult(
                jobId,
                request.dryRun(),
                request.scope(),
                0,
                0,
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                1,
                Map.of(ActivityProductPaginationRunner.StopReason.UNKNOWN.name(), 1L),
                List.of());
    }

    private String statusFromCounts(int scanned, int success, int incomplete, int failed) {
        if (scanned == 0 || failed >= scanned) {
            return "FAILED";
        }
        if (failed > 0 || incomplete > 0 || success < scanned) {
            return "PARTIAL";
        }
        return "SUCCESS";
    }

    private String statusForStopReason(String stopReason, boolean complete) {
        if (complete) {
            return "SUCCESS";
        }
        if (ActivityProductPaginationRunner.StopReason.MAX_PAGES_REACHED.name().equals(stopReason)) {
            return "INCOMPLETE_MAX_PAGES";
        }
        if (ActivityProductPaginationRunner.StopReason.MAX_ROWS_REACHED.name().equals(stopReason)) {
            return "INCOMPLETE_MAX_ROWS";
        }
        if (ActivityProductPaginationRunner.StopReason.API_ERROR.name().equals(stopReason)
                || ActivityProductPaginationRunner.StopReason.INVALID_RESPONSE.name().equals(stopReason)) {
            return "FAILED";
        }
        return "INCOMPLETE_CURSOR_ERROR";
    }

    private boolean isFailedStopReason(String stopReason) {
        return ActivityProductPaginationRunner.StopReason.API_ERROR.name().equals(stopReason)
                || ActivityProductPaginationRunner.StopReason.INVALID_RESPONSE.name().equals(stopReason);
    }

    private NormalizedRequest normalize(BackfillRequest request) {
        BackfillRequest safe = request == null
                ? new BackfillRequest(null, List.of(), null, null, null, null, true, false)
                : request;
        String scope = normalizeScope(safe.scope());
        List<String> activityIds = safe.activityIds() == null
                ? List.of()
                : safe.activityIds().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if ("CUSTOM_ACTIVITY_IDS".equals(scope) && activityIds.isEmpty()) {
            throw BusinessException.param("CUSTOM_ACTIVITY_IDS 必须提供 activityIds");
        }
        return new NormalizedRequest(
                scope,
                activityIds,
                normalizePositive(safe.pageSize(), DEFAULT_PAGE_SIZE, 20),
                normalizePositive(safe.maxActivities(), DEFAULT_MAX_ACTIVITIES, MAX_ACTIVITIES),
                normalizePositive(safe.maxPagesPerActivity(), DEFAULT_MAX_PAGES, Integer.MAX_VALUE),
                normalizePositive(safe.maxRowsPerActivity(), DEFAULT_MAX_ROWS, Integer.MAX_VALUE),
                safe.dryRun() == null || safe.dryRun(),
                Boolean.TRUE.equals(safe.confirm()));
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "RECENT_30D";
        }
        String normalized = scope.trim().toUpperCase();
        if ("CUSTOM".equals(normalized)) {
            return "CUSTOM_ACTIVITY_IDS";
        }
        return normalized;
    }

    private String mapperScope(String scope) {
        return "CUSTOM_ACTIVITY_IDS".equals(scope) ? "CUSTOM" : scope;
    }

    private int normalizePositive(Integer value, int defaultValue, int maxValue) {
        int normalized = value == null || value <= 0 ? defaultValue : value;
        return Math.min(Math.max(normalized, 1), maxValue);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    public record BackfillRequest(
            String scope,
            List<String> activityIds,
            Integer pageSize,
            Integer maxActivities,
            Integer maxPagesPerActivity,
            Integer maxRowsPerActivity,
            Boolean dryRun,
            Boolean confirm) {
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
            List<ProductSyncDryRunProbeService.ActivityDryRunResult> topGapActivities) {
    }

    private record NormalizedRequest(
            String scope,
            List<String> activityIds,
            int pageSize,
            int maxActivities,
            int maxPagesPerActivity,
            int maxRowsPerActivity,
            boolean dryRun,
            boolean confirm) {
    }
}
