package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 商品同步只读 dry-run 探针服务。
 */
@Service
public class ProductSyncDryRunProbeService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_MAX_PAGES = 1000;
    private static final int DEFAULT_MAX_ACTIVITIES = 50;
    private static final int MAX_ALLOWED_ACTIVITIES = 200;
    private static final int DEFAULT_MAX_ROWS_PER_ACTIVITY = 50_000;

    private final DouyinProductGateway douyinProductGateway;
    private final ProductSnapshotMapper snapshotMapper;
    @SuppressWarnings("unused")
    private final ProductOperationStateMapper operationStateMapper;
    private final ColonelsettlementActivityMapper activityMapper;

    public ProductSyncDryRunProbeService(
            DouyinProductGateway douyinProductGateway,
            ProductSnapshotMapper snapshotMapper,
            ProductOperationStateMapper operationStateMapper,
            ColonelsettlementActivityMapper activityMapper) {
        this.douyinProductGateway = douyinProductGateway;
        this.snapshotMapper = snapshotMapper;
        this.operationStateMapper = operationStateMapper;
        this.activityMapper = activityMapper;
    }

    public ActivityDryRunResult deepDryRun(ActivityDeepDryRunRequest request) {
        ActivityDeepDryRunRequest normalized = normalizeDeepRequest(request);
        ComputedActivityDryRun computed = runActivity(normalized.activityId(), normalized.pageSize(),
                normalized.maxPages(), DEFAULT_MAX_ROWS_PER_ACTIVITY, normalized.stopOnRepeatedCursor(), normalized.dryRun());
        return computed.result();
    }

    public FullDryRunResult fullDryRun(FullDryRunRequest request) {
        return fullDryRun(request, null);
    }

    public FullDryRunResult fullDryRun(FullDryRunRequest request, BiConsumer<Integer, ActivityDryRunResult> activityProgressCallback) {
        FullDryRunRequest normalized = normalizeFullRequest(request);
        List<String> activityIds = resolveActivityIds(normalized);
        List<ActivityDryRunResult> results = new ArrayList<>();
        Set<String> globalProductIds = new LinkedHashSet<>();
        List<ApiError> apiErrors = new ArrayList<>();
        long apiFetchedRows = 0L;
        long dbRows = 0L;
        long estimatedGapRows = 0L;
        int activitiesWithProducts = 0;
        int reachedMaxPages = 0;
        int stillHasNextAfterMaxPages = 0;
        int activitiesSuccess = 0;
        int activitiesIncomplete = 0;
        int activitiesFailed = 0;
        Map<String, Long> stopReasonStats = new LinkedHashMap<>();
        int activityIndex = 0;
        for (String activityId : activityIds) {
            activityIndex++;
            ComputedActivityDryRun computed = runActivity(
                    activityId,
                    normalized.pageSize(),
                    normalized.maxPagesPerActivity(),
                    normalized.maxRowsPerActivity(),
                    true,
                    normalized.dryRun());
            ActivityDryRunResult result = computed.result();
            if (activityProgressCallback != null) {
                activityProgressCallback.accept(activityIndex, result);
            }
            results.add(result);
            globalProductIds.addAll(computed.productIds());
            apiFetchedRows += result.totalFetchedRows();
            dbRows += result.currentDbRowsForActivity();
            estimatedGapRows += result.estimatedGapRows();
            stopReasonStats.merge(result.stoppedReason(), 1L, Long::sum);
            if (result.totalFetchedRows() > 0) {
                activitiesWithProducts++;
            }
            if (ActivityProductPaginationRunner.StopReason.DONE_NO_MORE.name().equals(result.stoppedReason())) {
                activitiesSuccess++;
            } else if (ActivityProductPaginationRunner.StopReason.API_ERROR.name().equals(result.stoppedReason())
                    || ActivityProductPaginationRunner.StopReason.INVALID_RESPONSE.name().equals(result.stoppedReason())) {
                activitiesFailed++;
            } else {
                activitiesIncomplete++;
            }
            if (ActivityProductPaginationRunner.StopReason.MAX_PAGES_REACHED.name().equals(result.stoppedReason())) {
                reachedMaxPages++;
                if (result.stillHasNextWhenStopped()) {
                    stillHasNextAfterMaxPages++;
                }
            }
            if (ActivityProductPaginationRunner.StopReason.API_ERROR.name().equals(result.stoppedReason())) {
                apiErrors.add(new ApiError(activityId, result.stoppedReason(), String.join("; ", result.warnings())));
            }
        }

        List<ActivityDryRunResult> topGapActivities = results.stream()
                .sorted(Comparator.comparingLong(ActivityDryRunResult::estimatedGapRows).reversed())
                .limit(10)
                .toList();
        List<ActivityDryRunResult> topLargeActivities = results.stream()
                .sorted(Comparator.comparingLong(ActivityDryRunResult::totalFetchedRows).reversed())
                .limit(10)
                .toList();
        return new FullDryRunResult(
                true,
                normalized.activityScope(),
                results.size(),
                activitiesWithProducts,
                reachedMaxPages,
                stillHasNextAfterMaxPages,
                apiFetchedRows,
                globalProductIds.size(),
                dbRows,
                estimatedGapRows,
                activitiesSuccess,
                activitiesIncomplete,
                activitiesFailed,
                stopReasonStats,
                topGapActivities,
                topLargeActivities,
                apiErrors,
                results);
    }

    private ComputedActivityDryRun runActivity(
            String activityId,
            int pageSize,
            int maxPages,
            int maxRows,
            boolean stopOnRepeatedCursor,
            boolean dryRun) {
        DouyinProductGateway.ActivityProductQueryRequest baseRequest =
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, activityId, 4L, 1L, pageSize, null, null, null, null, 1L, null, null);
        ActivityProductPaginationRunner.Result pageResult = ActivityProductPaginationRunner.run(
                baseRequest,
                new ActivityProductPaginationRunner.Options(pageSize, maxPages, maxRows, stopOnRepeatedCursor),
                douyinProductGateway::queryActivityProducts,
                page -> ActivityProductPaginationRunner.PageWriteStats.ZERO,
                page -> {
                });
        long dbRows = countDbRows(activityId);
        long gapRows = Math.max(0L, pageResult.fetchedRows() - dbRows);
        long missingIfCurrentMax100 = Math.max(0L, pageResult.fetchedRows() - (long) DEFAULT_PAGE_SIZE * 100L);
        ActivityDryRunResult result = new ActivityDryRunResult(
                activityId,
                dryRun,
                pageSize,
                maxPages,
                pageResult.pagesFetched(),
                pageResult.fetchedRows(),
                pageResult.distinctProductIds(),
                pageResult.duplicateProductIds(),
                dbRows,
                gapRows,
                missingIfCurrentMax100,
                pageResult.stopReason().name(),
                pageResult.stillHasNextWhenStopped(),
                pageResult.pageSamples(),
                pageResult.warnings());
        return new ComputedActivityDryRun(result, pageResult.productIds());
    }

    private long countDbRows(String activityId) {
        Long count = snapshotMapper.selectCount(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .eq(ProductSnapshot::getDeleted, 0));
        return count == null ? 0L : count;
    }

    private List<String> resolveActivityIds(FullDryRunRequest request) {
        if (isCustomScope(request.activityScope())) {
            return normalizeActivityIds(request.activityIds()).stream()
                    .limit(request.maxActivities())
                    .toList();
        }
        LocalDateTime recentSince = switch (request.activityScope().toUpperCase()) {
            case "RECENT_30D" -> LocalDateTime.now().minusDays(30);
            case "RECENT_90D" -> LocalDateTime.now().minusDays(90);
            default -> null;
        };
        return activityMapper.selectActivityIdsForProductSyncProbe(
                request.activityScope().toUpperCase(),
                request.maxActivities(),
                recentSince,
                null);
    }

    private ActivityDeepDryRunRequest normalizeDeepRequest(ActivityDeepDryRunRequest request) {
        if (request == null || !StringUtils.hasText(request.activityId())) {
            throw new IllegalArgumentException("activityId is required");
        }
        if (!Boolean.TRUE.equals(request.dryRun())) {
            throw new IllegalArgumentException("dryRun must be true");
        }
        return new ActivityDeepDryRunRequest(
                request.activityId().trim(),
                normalizePageSize(request.pageSize()),
                normalizeMaxPages(request.maxPages()),
                request.stopOnRepeatedCursor() == null || request.stopOnRepeatedCursor(),
                true);
    }

    private FullDryRunRequest normalizeFullRequest(FullDryRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!Boolean.TRUE.equals(request.dryRun())) {
            throw new IllegalArgumentException("dryRun must be true");
        }
        String scope = StringUtils.hasText(request.activityScope())
                ? normalizeScope(request.activityScope())
                : "ACTIVE_ONLY";
        List<String> activityIds = normalizeActivityIds(request.activityIds());
        if (isCustomScope(scope) && activityIds.isEmpty()) {
            throw new IllegalArgumentException("activityIds is required for CUSTOM_ACTIVITY_IDS");
        }
        int maxActivities = Math.min(Math.max(request.maxActivities() <= 0 ? DEFAULT_MAX_ACTIVITIES : request.maxActivities(), 1),
                MAX_ALLOWED_ACTIVITIES);
        return new FullDryRunRequest(
                scope,
                activityIds,
                maxActivities,
                normalizePageSize(request.pageSize()),
                normalizeMaxPages(request.maxPagesPerActivity()),
                normalizeMaxRows(request.maxRowsPerActivity()),
                true);
    }

    private int normalizePageSize(int value) {
        return Math.min(Math.max(value <= 0 ? DEFAULT_PAGE_SIZE : value, 1), 20);
    }

    private int normalizeMaxPages(int value) {
        return Math.max(value <= 0 ? DEFAULT_MAX_PAGES : value, 1);
    }

    private int normalizeMaxRows(int value) {
        return Math.max(value <= 0 ? DEFAULT_MAX_ROWS_PER_ACTIVITY : value, 1);
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "ACTIVE_ONLY";
        }
        return scope.trim().toUpperCase();
    }

    private boolean isCustomScope(String scope) {
        return "CUSTOM".equalsIgnoreCase(scope) || "CUSTOM_ACTIVITY_IDS".equalsIgnoreCase(scope);
    }

    private List<String> normalizeActivityIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private record ComputedActivityDryRun(ActivityDryRunResult result, Set<String> productIds) {
    }

    public record ActivityDeepDryRunRequest(
            String activityId,
            int pageSize,
            int maxPages,
            Boolean stopOnRepeatedCursor,
            Boolean dryRun) {
    }

    public record FullDryRunRequest(
            @JsonAlias("scope")
            String activityScope,
            List<String> activityIds,
            int maxActivities,
            int pageSize,
            int maxPagesPerActivity,
            int maxRowsPerActivity,
            Boolean dryRun) {
        public FullDryRunRequest(
                String activityScope,
                List<String> activityIds,
                int maxActivities,
                int pageSize,
                int maxPagesPerActivity,
                Boolean dryRun) {
            this(activityScope, activityIds, maxActivities, pageSize, maxPagesPerActivity, 0, dryRun);
        }
    }

    public record ActivityDryRunResult(
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
            List<ActivityProductPaginationRunner.PageSummary> pageSamples,
            List<String> warnings) {
    }

    public record FullDryRunResult(
            boolean dryRun,
            String activityScope,
            int activitiesScanned,
            int activitiesWithProducts,
            int activitiesReachedMaxPages,
            int activitiesStillHasNextAfterMaxPages,
            long apiFetchedRows,
            long apiDistinctProductIds,
            long dbRowsForScannedActivities,
            long estimatedGapRows,
            int activitiesSuccess,
            int activitiesIncomplete,
            int activitiesFailed,
            Map<String, Long> stopReasonStats,
            List<ActivityDryRunResult> topGapActivities,
            List<ActivityDryRunResult> topLargeActivities,
            List<ApiError> apiErrors,
            List<ActivityDryRunResult> activityResults) {
    }

    public record ApiError(String activityId, String stopReason, String message) {
    }
}
