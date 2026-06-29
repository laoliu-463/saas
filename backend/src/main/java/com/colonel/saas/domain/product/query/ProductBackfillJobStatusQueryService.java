package com.colonel.saas.domain.product.query;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.product.application.ProductBackfillJobMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 活动商品 backfill job 状态查询服务。
 */
@Service
public class ProductBackfillJobStatusQueryService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProductBackfillJobStatusRepository repository;
    private final ProductBackfillJobMetadata metadata = new ProductBackfillJobMetadata();

    public ProductBackfillJobStatusQueryService(ProductBackfillJobStatusRepository repository) {
        this.repository = repository;
    }

    public ProductBackfillJobStatusView getJobStatus(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            throw BusinessException.param("jobId 不能为空");
        }
        ProductBackfillJobStatusSnapshot snapshot = repository.findLatestByJobId(jobId.trim())
                .orElseThrow(() -> BusinessException.notFound("未找到对应的 backfill job"));
        Map<String, Object> requestMeta = metadata.read(snapshot.requestParamsJson());
        return new ProductBackfillJobStatusView(
                snapshot.jobId(),
                snapshot.status(),
                Boolean.TRUE.equals(snapshot.dryRun()),
                snapshot.scope(),
                activityCount(snapshot.activitiesScanned(), requestMeta),
                intOrZero(snapshot.activitiesSuccess()),
                intOrZero(snapshot.activitiesIncomplete()),
                intOrZero(snapshot.activitiesFailed()),
                longOrZero(snapshot.apiFetchedRows()),
                longOrZero(snapshot.apiDistinctProductIds()),
                metadata.longValue(requestMeta, "dbRowsBefore"),
                metadata.longValue(requestMeta, "estimatedGapRows"),
                intOrZero(snapshot.inserted()),
                intOrZero(snapshot.updated()),
                intOrZero(snapshot.skipped()),
                intOrZero(snapshot.failed()),
                readStopReasonStats(snapshot.stopReasonStatsJson()),
                readCurrentActivityId(requestMeta),
                requestMeta.get("lastProgressAt") == null ? null : requestMeta.get("lastProgressAt").toString(),
                metadata.longValue(requestMeta, "lockWaitCount"),
                metadata.longValue(requestMeta, "deadlockRetryCount"),
                0,
                snapshot.startedAt() == null ? null : snapshot.startedAt().toString(),
                snapshot.finishedAt() == null ? null : snapshot.finishedAt().toString());
    }

    private String readCurrentActivityId(Map<String, Object> requestMeta) {
        Object currentActivityId = requestMeta.get("currentActivityId");
        return currentActivityId == null || !StringUtils.hasText(currentActivityId.toString())
                ? null
                : currentActivityId.toString();
    }

    private Map<String, Long> readStopReasonStats(String stopReasonStatsJson) {
        if (!StringUtils.hasText(stopReasonStatsJson)) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = OBJECT_MAPPER.readValue(stopReasonStatsJson, Map.class);
            Map<String, Long> converted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                Long value = toLong(entry.getValue());
                if (entry.getKey() != null && value != null) {
                    converted.put(entry.getKey(), value);
                }
            }
            return converted;
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private Long toLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int activityCount(Integer snapshotCount, Map<String, Object> requestMeta) {
        int value = intOrZero(snapshotCount);
        if (value > 0) {
            return value;
        }
        long metadataTotal = metadata.longValue(requestMeta, "activitiesTotal");
        if (metadataTotal <= 0L) {
            return 0;
        }
        return metadataTotal > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) metadataTotal;
    }

    private long longOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
