package com.colonel.saas.domain.product.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductBackfillJobMetadataTest {

    private final ProductBackfillJobMetadata metadata = new ProductBackfillJobMetadata();

    @Test
    void startedProgressAndFinishedMetadata_shouldPreserveRequestAndUpdateProgressFields() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 27, 15, 0);
        String started = metadata.started("{\"scope\":\"CUSTOM_ACTIVITY_IDS\"}", startedAt);

        Map<String, Object> startedMap = metadata.read(started);
        assertThat(startedMap).containsEntry("scope", "CUSTOM_ACTIVITY_IDS");
        assertThat(startedMap).containsEntry("currentActivityId", "");
        assertThat(startedMap).containsEntry("lastProgressAt", startedAt.toString());
        assertThat(metadata.longValue(startedMap, "lockWaitCount")).isZero();
        assertThat(metadata.longValue(startedMap, "deadlockRetryCount")).isZero();

        LocalDateTime progressAt = startedAt.plusMinutes(1);
        String progress = metadata.progress(started, "ACT-2", progressAt);
        assertThat(metadata.read(progress))
                .containsEntry("currentActivityId", "ACT-2")
                .containsEntry("lastProgressAt", progressAt.toString());

        LocalDateTime finishedAt = startedAt.plusMinutes(2);
        String finished = metadata.finished(
                progress,
                new ProductBackfillJobMetadata.FinishMetrics(2L, 3L, 10L, 4L),
                finishedAt);

        Map<String, Object> finishedMap = metadata.read(finished);
        assertThat(finishedMap).containsEntry("currentActivityId", "");
        assertThat(finishedMap).containsEntry("lastProgressAt", finishedAt.toString());
        assertThat(metadata.longValue(finishedMap, "lockWaitCount")).isEqualTo(2L);
        assertThat(metadata.longValue(finishedMap, "deadlockRetryCount")).isEqualTo(3L);
        assertThat(metadata.longValue(finishedMap, "dbRowsBefore")).isEqualTo(10L);
        assertThat(metadata.longValue(finishedMap, "estimatedGapRows")).isEqualTo(4L);
    }

    @Test
    void startedWithAsyncIdempotencyKey_shouldPreserveKeyThroughProgressAndFinished() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 28, 21, 30);
        String started = metadata.started(
                "{\"scope\":\"CUSTOM_ACTIVITY_IDS\"}",
                "async-key-1",
                startedAt);

        assertThat(metadata.read(started))
                .containsEntry("scope", "CUSTOM_ACTIVITY_IDS")
                .containsEntry("asyncIdempotencyKey", "async-key-1");

        String progress = metadata.progress(
                started,
                "ACT-1",
                startedAt.plusMinutes(1));
        assertThat(metadata.read(progress))
                .containsEntry("asyncIdempotencyKey", "async-key-1")
                .containsEntry("currentActivityId", "ACT-1");

        String finished = metadata.finished(
                progress,
                new ProductBackfillJobMetadata.FinishMetrics(1L, 2L, 3L, 4L),
                startedAt.plusMinutes(2));
        assertThat(metadata.read(finished))
                .containsEntry("asyncIdempotencyKey", "async-key-1")
                .containsEntry("currentActivityId", "");
    }

    @Test
    void progress_shouldKeepOriginalValueWhenMetadataJsonIsInvalid() {
        String invalid = "{not-json";

        String progress = metadata.progress(
                invalid,
                "ACT-1",
                LocalDateTime.of(2026, 6, 27, 15, 30));

        assertThat(progress).isEqualTo(invalid);
        assertThat(metadata.read(progress)).isEmpty();
    }

    @Test
    void retryProgress_shouldRecordCurrentActivityAndDeadlockRetryCount() {
        String started = metadata.started(
                "{\"scope\":\"CUSTOM_ACTIVITY_IDS\"}",
                LocalDateTime.parse("2026-06-29T12:30:00"));

        String progress = metadata.retryProgress(
                started,
                "ACT-1",
                2L,
                LocalDateTime.parse("2026-06-29T12:31:00"));

        assertThat(metadata.read(progress))
                .containsEntry("scope", "CUSTOM_ACTIVITY_IDS")
                .containsEntry("currentActivityId", "ACT-1")
                .containsEntry("lastProgressAt", "2026-06-29T12:31")
                .containsEntry("deadlockRetryCount", 2);
    }
}
