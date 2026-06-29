package com.colonel.saas.domain.product.query;

import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductBackfillJobStatusQueryServiceTest {

    private final StubRepository repository = new StubRepository();
    private final ProductBackfillJobStatusQueryService service =
            new ProductBackfillJobStatusQueryService(repository);

    @Test
    void getJobStatus_shouldTrimJobIdAndProjectSnapshotWithMetadata() {
        repository.result = Optional.of(new ProductBackfillJobStatusSnapshot(
                "job-1",
                "RUNNING",
                true,
                "CUSTOM_ACTIVITY_IDS",
                null,
                2,
                null,
                1,
                100L,
                null,
                null,
                3,
                null,
                4,
                "{\"currentActivityId\":\"ACT-1\",\"lastProgressAt\":\"2026-06-29T12:00:00\","
                        + "\"dbRowsBefore\":12,\"estimatedGapRows\":\"5\","
                        + "\"lockWaitCount\":2,\"deadlockRetryCount\":\"3\"}",
                "{\"DONE_NO_MORE\":2,\"FAILED_DB\":1}",
                LocalDateTime.of(2026, 6, 29, 11, 59),
                null));

        ProductBackfillJobStatusView status = service.getJobStatus(" job-1 ");

        assertThat(repository.requestedJobId).isEqualTo("job-1");
        assertThat(status.jobId()).isEqualTo("job-1");
        assertThat(status.status()).isEqualTo("RUNNING");
        assertThat(status.dryRun()).isTrue();
        assertThat(status.activitiesScanned()).isZero();
        assertThat(status.activitiesSuccess()).isEqualTo(2);
        assertThat(status.activitiesIncomplete()).isZero();
        assertThat(status.activitiesFailed()).isEqualTo(1);
        assertThat(status.apiFetchedRows()).isEqualTo(100L);
        assertThat(status.apiDistinctProductIds()).isZero();
        assertThat(status.dbRowsBefore()).isEqualTo(12L);
        assertThat(status.estimatedGapRows()).isEqualTo(5L);
        assertThat(status.inserted()).isZero();
        assertThat(status.updated()).isEqualTo(3);
        assertThat(status.skipped()).isZero();
        assertThat(status.failed()).isEqualTo(4);
        assertThat(status.stopReasonStats())
                .containsEntry("DONE_NO_MORE", 2L)
                .containsEntry("FAILED_DB", 1L);
        assertThat(status.currentActivityId()).isEqualTo("ACT-1");
        assertThat(status.lastProgressAt()).isEqualTo("2026-06-29T12:00:00");
        assertThat(status.lockWaitCount()).isEqualTo(2L);
        assertThat(status.deadlockRetryCount()).isEqualTo(3L);
        assertThat(status.unchanged()).isZero();
        assertThat(status.startedAt()).isEqualTo("2026-06-29T11:59");
        assertThat(status.finishedAt()).isNull();
    }

    @Test
    void getJobStatus_shouldUseRunningMetadataTotalWhenSnapshotCountIsNotFinished() {
        repository.result = Optional.of(new ProductBackfillJobStatusSnapshot(
                "job-running",
                "RUNNING",
                false,
                "CUSTOM_ACTIVITY_IDS",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "{\"currentActivityId\":\"ACT-2\",\"activitiesTotal\":5,\"activitiesProcessed\":3,"
                        + "\"lastProgressAt\":\"2026-06-29T15:00:00\"}",
                null,
                LocalDateTime.of(2026, 6, 29, 14, 59),
                null));

        ProductBackfillJobStatusView status = service.getJobStatus("job-running");

        assertThat(status.activitiesScanned()).isEqualTo(5);
        assertThat(status.currentActivityId()).isEqualTo("ACT-2");
        assertThat(status.lastProgressAt()).isEqualTo("2026-06-29T15:00:00");
    }

    @Test
    void getJobStatus_shouldRejectBlankJobId() {
        assertThatThrownBy(() -> service.getJobStatus(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("jobId 不能为空");
    }

    @Test
    void getJobStatus_shouldRejectMissingJob() {
        repository.result = Optional.empty();

        assertThatThrownBy(() -> service.getJobStatus("job-missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到对应的 backfill job");
    }

    private static final class StubRepository implements ProductBackfillJobStatusRepository {
        private String requestedJobId;
        private Optional<ProductBackfillJobStatusSnapshot> result = Optional.empty();

        @Override
        public Optional<ProductBackfillJobStatusSnapshot> findLatestByJobId(String jobId) {
            this.requestedJobId = jobId;
            return result;
        }
    }
}
