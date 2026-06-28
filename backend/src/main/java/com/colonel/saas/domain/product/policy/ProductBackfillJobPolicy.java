package com.colonel.saas.domain.product.policy;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 活动商品 backfill job 身份和状态规则。
 */
public final class ProductBackfillJobPolicy {

    public static final String JOB_ID_PREFIX = "product-backfill-";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED_LOCKED = "FAILED_LOCKED";
    public static final String STOP_REASON_FAILED_LOCKED = "FAILED_LOCKED";
    public static final String STOP_REASON_DEADLOCK_RETRY_EXHAUSTED = "DEADLOCK_RETRY_EXHAUSTED";
    public static final String STOP_REASON_UPSTREAM_API_ERROR = "UPSTREAM_API_ERROR";
    public static final String STOP_REASON_DB_ERROR = "DB_ERROR";
    public static final String STOP_REASON_LOCK_ERROR = "LOCK_ERROR";
    public static final String STOP_REASON_TIMEOUT_ERROR = "TIMEOUT_ERROR";
    public static final String STOP_REASON_UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String STOP_REASON_API_ERROR = "API_ERROR";
    public static final String STOP_REASON_INVALID_RESPONSE = "INVALID_RESPONSE";
    public static final String STOP_REASON_MAX_PAGES_REACHED = "MAX_PAGES_REACHED";
    public static final String STOP_REASON_MAX_ROWS_REACHED = "MAX_ROWS_REACHED";

    public String newJobId(UUID uuid) {
        return JOB_ID_PREFIX + Objects.requireNonNull(uuid, "uuid");
    }

    public String statusFromCounts(int scanned, int success, int incomplete, int failed) {
        if (scanned == 0 || failed >= scanned) {
            return STATUS_FAILED;
        }
        if (failed > 0 || incomplete > 0 || success < scanned) {
            return STATUS_PARTIAL;
        }
        return STATUS_SUCCESS;
    }

    public String statusForStopReason(String stopReason, boolean complete) {
        if (complete) {
            return STATUS_SUCCESS;
        }
        if (STOP_REASON_MAX_PAGES_REACHED.equals(stopReason)) {
            return "INCOMPLETE_MAX_PAGES";
        }
        if (STOP_REASON_MAX_ROWS_REACHED.equals(stopReason)) {
            return "INCOMPLETE_MAX_ROWS";
        }
        if (isFailedStopReason(stopReason)) {
            return STATUS_FAILED;
        }
        return "INCOMPLETE_CURSOR_ERROR";
    }

    public boolean isFailedStopReason(String stopReason) {
        return STOP_REASON_API_ERROR.equals(stopReason)
                || STOP_REASON_INVALID_RESPONSE.equals(stopReason)
                || STOP_REASON_UPSTREAM_API_ERROR.equals(stopReason)
                || STOP_REASON_DB_ERROR.equals(stopReason)
                || STOP_REASON_LOCK_ERROR.equals(stopReason)
                || STOP_REASON_TIMEOUT_ERROR.equals(stopReason)
                || STOP_REASON_UNKNOWN_ERROR.equals(stopReason)
                || STOP_REASON_FAILED_LOCKED.equals(stopReason)
                || STOP_REASON_DEADLOCK_RETRY_EXHAUSTED.equals(stopReason);
    }

    public String asyncIdempotencyKey(BackfillRequestIdentity identity) {
        BackfillRequestIdentity safe = Objects.requireNonNull(identity, "identity");
        return String.join("|",
                nullToEmpty(safe.requestedBy()),
                nullToEmpty(safe.scope()),
                joinActivityIds(safe.activityIds()),
                String.valueOf(safe.pageSize()),
                String.valueOf(safe.maxActivities()),
                String.valueOf(safe.maxPagesPerActivity()),
                String.valueOf(safe.maxRowsPerActivity()),
                String.valueOf(safe.dryRun()),
                String.valueOf(safe.confirm()),
                nullToEmpty(safe.displayRefreshMode()));
    }

    private String joinActivityIds(List<String> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return "";
        }
        return activityIds.stream()
                .map(this::nullToEmpty)
                .collect(Collectors.joining(","));
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public record BackfillRequestIdentity(
            String scope,
            List<String> activityIds,
            int pageSize,
            int maxActivities,
            int maxPagesPerActivity,
            int maxRowsPerActivity,
            boolean dryRun,
            boolean confirm,
            String displayRefreshMode,
            UUID requestedBy) {
    }
}
