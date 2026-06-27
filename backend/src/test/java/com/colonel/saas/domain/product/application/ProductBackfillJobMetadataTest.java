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
    void progress_shouldKeepOriginalValueWhenMetadataJsonIsInvalid() {
        String invalid = "{not-json";

        String progress = metadata.progress(
                invalid,
                "ACT-1",
                LocalDateTime.of(2026, 6, 27, 15, 30));

        assertThat(progress).isEqualTo(invalid);
        assertThat(metadata.read(progress)).isEmpty();
    }
}
