package com.colonel.saas.domain.product.policy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductBackfillFailurePolicyTest {

    private final ProductBackfillFailurePolicy policy = new ProductBackfillFailurePolicy();

    @Test
    void dominantStopReason_shouldKeepFirstNonSuccessReason() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("DONE_NO_MORE", 3L);
        stats.put("API_ERROR", 1L);
        stats.put("FAILED_LOCKED", 1L);

        assertThat(policy.dominantStopReason(stats)).isEqualTo("API_ERROR");
        assertThat(policy.dominantStopReason(Map.of("DONE_NO_MORE", 1L))).isEmpty();
        assertThat(policy.dominantStopReason(null)).isEmpty();
    }

    @Test
    void normalizeRawCause_shouldKeepLegacyStopReasonMapping() {
        assertThat(policy.normalizeRawCause(null)).isEqualTo("UNKNOWN_ERROR");
        assertThat(policy.normalizeRawCause("")).isEqualTo("UNKNOWN_ERROR");
        assertThat(policy.normalizeRawCause("FAILED_LOCKED")).isEqualTo("LOCK_ERROR");
        assertThat(policy.normalizeRawCause("DEADLOCK_RETRY_EXHAUSTED")).isEqualTo("DB_ERROR");
        assertThat(policy.normalizeRawCause("API_ERROR")).isEqualTo("UPSTREAM_API_ERROR");
        assertThat(policy.normalizeRawCause("INVALID_RESPONSE")).isEqualTo("UPSTREAM_API_ERROR");
        assertThat(policy.normalizeRawCause("DB_ERROR")).isEqualTo("DB_ERROR");
        assertThat(policy.normalizeRawCause("UNEXPECTED")).isEqualTo("UNKNOWN_ERROR");
    }

    @Test
    void stopReasonForException_shouldClassifyDeadlockBeforeLegacyApiFallback() {
        RuntimeException apiError = new RuntimeException("HTTP 500 upstream failed");
        DeadlockLoserDataAccessException deadlock =
                new DeadlockLoserDataAccessException("deadlock detected SQLSTATE 40P01", apiError);

        assertThat(policy.stopReasonForException(apiError, null)).isEqualTo("API_ERROR");
        assertThat(policy.stopReasonForException(deadlock, null)).isEqualTo("DEADLOCK_RETRY_EXHAUSTED");
        assertThat(policy.stopReasonForException(apiError, "DB_ERROR")).isEqualTo("DB_ERROR");
    }

    @Test
    void failureErrorMessage_shouldKeepLegacyContextFormat() {
        RuntimeException ex = new RuntimeException(
                "HTTP 500 upstream failed",
                new IllegalStateException("root cause"));

        String message = policy.buildFailureErrorMessage(
                "job-1",
                "ACT-1",
                "CUSTOM_ACTIVITY_IDS",
                "API_ERROR",
                ex,
                "lock=held");

        assertThat(message).isEqualTo(
                "type=FAILED; rawCause=UPSTREAM_API_ERROR; exceptionClass=java.lang.RuntimeException; "
                        + "rootCauseClass=java.lang.IllegalStateException; jobId=job-1; activityId=ACT-1; "
                        + "scope=CUSTOM_ACTIVITY_IDS; stopReason=API_ERROR; rootCause=root cause; sqlState=; "
                        + "lockInfo=lock=held; httpStatus=500; sdkCode=; message=HTTP 500 upstream failed");
    }

    @Test
    void failureErrorMessage_shouldExtractSqlStateFromCauseChain() {
        RuntimeException ex = new RuntimeException(
                "wrapper",
                new CannotAcquireLockException("lock not available SQLSTATE 55P03"));

        String message = policy.buildFailureErrorMessage(
                "job-2",
                null,
                null,
                "DEADLOCK_RETRY_EXHAUSTED",
                ex,
                null,
                null,
                "explicit message");

        assertThat(message)
                .contains("rawCause=DB_ERROR")
                .contains("activityId=")
                .contains("scope=")
                .contains("sqlState=55P03")
                .contains("message=explicit message");
    }

    @Test
    void failedLockErrorMessage_shouldKeepLegacyOwnerSnapshotFields() {
        ProductBackfillFailurePolicy.JobLockSnapshot owner =
                new ProductBackfillFailurePolicy.JobLockSnapshot(
                        "PRODUCT_BACKFILL_ACTIVITY:ACT-1",
                        "owner-job",
                        "ACT-1",
                        "ACTIVITY",
                        "PRODUCT_BACKFILL_ACTIVITY:ACT-1",
                        "{\"ownerJobId\":\"owner-job\"}",
                        120L,
                        "2026-06-29T12:00:00");

        String message = policy.buildFailedLockErrorMessage(
                "job-3",
                "ACT-1",
                "ACTIVITY",
                "PRODUCT_BACKFILL_ACTIVITY:ACT-1",
                owner,
                "活动回填锁被占用");

        assertThat(message).isEqualTo(
                "type=FAILED_LOCKED; jobId=job-3; activityId=ACT-1; scope=ACTIVITY; "
                        + "lockKey=PRODUCT_BACKFILL_ACTIVITY:ACT-1; ownerJobId=owner-job; "
                        + "ownerActivityId=ACT-1; ownerScope=ACTIVITY; lockValue={\"ownerJobId\":\"owner-job\"}; "
                        + "ttlSeconds=120; acquiredAt=2026-06-29T12:00:00; message=活动回填锁被占用");
    }
}
