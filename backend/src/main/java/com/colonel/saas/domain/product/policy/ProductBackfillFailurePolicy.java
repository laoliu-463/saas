package com.colonel.saas.domain.product.policy;

import java.util.Map;

/**
 * 活动商品 backfill 失败分类与日志上下文规则。
 */
public final class ProductBackfillFailurePolicy {

    private static final String SQLSTATE_DEADLOCK = "40P01";
    private static final String SQLSTATE_LOCK_NOT_AVAILABLE = "55P03";

    public String stopReasonForException(Throwable ex, String explicitStopReason) {
        if (hasText(explicitStopReason)) {
            return explicitStopReason;
        }
        if (isDeadlockLike(ex)) {
            return ProductBackfillJobPolicy.STOP_REASON_DEADLOCK_RETRY_EXHAUSTED;
        }
        return ProductBackfillJobPolicy.STOP_REASON_API_ERROR;
    }

    public String dominantStopReason(Map<String, Long> stopReasonStats) {
        if (stopReasonStats == null || stopReasonStats.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, Long> entry : stopReasonStats.entrySet()) {
            String reason = entry.getKey();
            if (hasText(reason) && !"DONE_NO_MORE".equals(reason)) {
                return reason;
            }
        }
        return "";
    }

    public String normalizeRawCause(String stopReason) {
        if (!hasText(stopReason)) {
            return ProductBackfillJobPolicy.STOP_REASON_UNKNOWN_ERROR;
        }
        if (ProductBackfillJobPolicy.STOP_REASON_UPSTREAM_API_ERROR.equals(stopReason)
                || ProductBackfillJobPolicy.STOP_REASON_DB_ERROR.equals(stopReason)
                || ProductBackfillJobPolicy.STOP_REASON_LOCK_ERROR.equals(stopReason)
                || ProductBackfillJobPolicy.STOP_REASON_TIMEOUT_ERROR.equals(stopReason)
                || ProductBackfillJobPolicy.STOP_REASON_UNKNOWN_ERROR.equals(stopReason)) {
            return stopReason;
        }
        if (ProductBackfillJobPolicy.STOP_REASON_FAILED_LOCKED.equals(stopReason)) {
            return ProductBackfillJobPolicy.STOP_REASON_LOCK_ERROR;
        }
        if (ProductBackfillJobPolicy.STOP_REASON_DEADLOCK_RETRY_EXHAUSTED.equals(stopReason)) {
            return ProductBackfillJobPolicy.STOP_REASON_DB_ERROR;
        }
        return switch (stopReason) {
            case ProductBackfillJobPolicy.STOP_REASON_INVALID_RESPONSE,
                    ProductBackfillJobPolicy.STOP_REASON_API_ERROR ->
                    ProductBackfillJobPolicy.STOP_REASON_UPSTREAM_API_ERROR;
            default -> ProductBackfillJobPolicy.STOP_REASON_UNKNOWN_ERROR;
        };
    }

    public String buildFailureErrorMessage(
            String jobId,
            String activityId,
            String scope,
            String stopReason,
            Throwable ex,
            String lockInfo) {
        return buildFailureErrorMessage(
                jobId,
                activityId,
                scope,
                stopReason,
                ex,
                lockInfo,
                null,
                null);
    }

    public String buildFailureErrorMessage(
            String jobId,
            String activityId,
            String scope,
            String stopReason,
            Throwable ex,
            String explicitRawCause,
            String explicitMessage) {
        return buildFailureErrorMessage(
                jobId,
                activityId,
                scope,
                stopReason,
                ex,
                null,
                explicitRawCause,
                explicitMessage);
    }

    public String buildFailureErrorMessage(
            String jobId,
            String activityId,
            String scope,
            String stopReason,
            Throwable ex,
            String lockInfo,
            String explicitRawCause,
            String explicitMessage) {
        String rawCause = hasText(explicitRawCause)
                ? explicitRawCause
                : normalizeRawCause(stopReason);
        String message = hasText(explicitMessage) ? explicitMessage : (ex == null ? "" : ex.getMessage());
        String exceptionClass = ex == null ? "N/A" : ex.getClass().getName();
        String rootCauseClass = ex == null ? "" : rootCauseClass(ex);
        return String.format(
                "type=%s; rawCause=%s; exceptionClass=%s; rootCauseClass=%s; jobId=%s; activityId=%s; scope=%s; stopReason=%s; rootCause=%s; sqlState=%s; lockInfo=%s; httpStatus=%s; sdkCode=%s; message=%s",
                "FAILED",
                rawCause,
                exceptionClass,
                rootCauseClass,
                jobId,
                activityId == null ? "" : activityId,
                scope == null ? "" : scope,
                stopReason,
                rootCauseMessage(ex),
                ex == null ? "" : sqlStateFromThrowable(ex),
                lockInfo == null ? "" : lockInfo,
                ex == null ? "" : httpStatusFromThrowable(ex),
                ex == null ? "" : sdkCodeFromThrowable(ex),
                message);
    }

    public String buildFailedLockErrorMessage(
            String jobId,
            String activityId,
            String lockScope,
            String lockKey,
            JobLockSnapshot ownerSnapshot,
            String extraMessage) {
        return String.format(
                "type=FAILED_LOCKED; jobId=%s; activityId=%s; scope=%s; lockKey=%s; ownerJobId=%s; ownerActivityId=%s; ownerScope=%s; lockValue=%s; ttlSeconds=%d; acquiredAt=%s; message=%s",
                jobId,
                activityId == null ? "" : activityId,
                lockScope,
                lockKey,
                ownerSnapshot == null ? "" : ownerSnapshot.ownerJobId(),
                ownerSnapshot == null ? "" : ownerSnapshot.ownerActivityId(),
                ownerSnapshot == null ? "" : ownerSnapshot.scope(),
                ownerSnapshot == null ? "" : ownerSnapshot.lockValue(),
                ownerSnapshot == null ? -1L : ownerSnapshot.ttlSeconds(),
                ownerSnapshot == null ? "" : ownerSnapshot.acquiredAt(),
                extraMessage);
    }

    public boolean isDeadlockLike(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (hasClassInHierarchy(cur, "org.springframework.dao.DeadlockLoserDataAccessException")
                    || hasClassInHierarchy(cur, "org.springframework.dao.CannotAcquireLockException")) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null) {
                String upper = msg.toUpperCase();
                if (upper.contains(SQLSTATE_DEADLOCK) || upper.contains("DEADLOCK DETECTED")) {
                    return true;
                }
                if (upper.contains(SQLSTATE_LOCK_NOT_AVAILABLE) || upper.contains("LOCK NOT AVAILABLE")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private String rootCauseMessage(Throwable ex) {
        if (ex == null) {
            return "";
        }
        Throwable cur = ex;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }

    private String rootCauseClass(Throwable ex) {
        if (ex == null) {
            return "";
        }
        Throwable cur = ex;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getClass().getName();
    }

    private String sqlStateFromThrowable(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String upper = msg.toUpperCase();
                if (upper.contains(SQLSTATE_DEADLOCK) || upper.contains(SQLSTATE_LOCK_NOT_AVAILABLE)) {
                    return upper.contains(SQLSTATE_DEADLOCK) ? SQLSTATE_DEADLOCK : SQLSTATE_LOCK_NOT_AVAILABLE;
                }
            }
            cur = cur.getCause();
        }
        return "";
    }

    private String httpStatusFromThrowable(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                int status = parseHttpStatus(msg);
                if (status > 0) {
                    return String.valueOf(status);
                }
            }
            cur = cur.getCause();
        }
        return "";
    }

    private String sdkCodeFromThrowable(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String name = cur.getClass().getName();
            if (name.contains("Sdk") || name.contains("SDK") || name.contains("Douyin")) {
                return cur.getClass().getSimpleName();
            }
            cur = cur.getCause();
        }
        return "";
    }

    private int parseHttpStatus(String message) {
        int idx = message.indexOf("HTTP ");
        if (idx >= 0 && message.length() >= idx + 8) {
            String digits = message.substring(idx + 5).replaceAll("[^0-9].*", "");
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException ignore) {
                // ignore malformed status fragments.
            }
        }
        return -1;
    }

    private boolean hasClassInHierarchy(Throwable ex, String className) {
        Class<?> type = ex.getClass();
        while (type != null) {
            if (className.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record JobLockSnapshot(
            String lockKey,
            String ownerJobId,
            String ownerActivityId,
            String scope,
            String ownerLockKey,
            String lockValue,
            long ttlSeconds,
            String acquiredAt) {
    }
}
