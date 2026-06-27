package com.colonel.saas.domain.product.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品 backfill job 的 request metadata 读写组件。
 *
 * <p>不新增 DB 字段，继续把进度、锁等待和 deadlock retry 计数写入
 * {@code product_sync_job_log.request_params_json}。</p>
 */
public class ProductBackfillJobMetadata {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String started(String requestJson, LocalDateTime now) {
        return merge(requestJson, Map.of(
                "currentActivityId", "",
                "lastProgressAt", now.toString(),
                "lockWaitCount", 0L,
                "deadlockRetryCount", 0L,
                "dbRowsBefore", 0L,
                "estimatedGapRows", 0L));
    }

    public String progress(String requestJson, String currentActivityId, LocalDateTime now) {
        return merge(requestJson, Map.of(
                "currentActivityId", currentActivityId == null ? "" : currentActivityId,
                "lastProgressAt", now.toString()));
    }

    public String finished(String requestJson, FinishMetrics metrics, LocalDateTime now) {
        FinishMetrics safe = metrics == null ? new FinishMetrics(0L, 0L, 0L, 0L) : metrics;
        return merge(requestJson, Map.of(
                "lockWaitCount", safe.lockWaitCount(),
                "deadlockRetryCount", safe.deadlockRetryCount(),
                "dbRowsBefore", safe.dbRowsBefore(),
                "estimatedGapRows", safe.estimatedGapRows(),
                "currentActivityId", "",
                "lastProgressAt", now.toString()));
    }

    public Map<String, Object> read(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(requestJson, Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    public long longValue(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key) || metadata.get(key) == null) {
            return 0L;
        }
        Object raw = metadata.get(key);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String merge(String original, Map<String, Object> updates) {
        if (original == null || original.isBlank()) {
            original = "{}";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(original, Map.class);
            Map<String, Object> merged = new LinkedHashMap<>(parsed == null ? Map.of() : parsed);
            merged.putAll(updates);
            return OBJECT_MAPPER.writeValueAsString(merged);
        } catch (JsonProcessingException ex) {
            return original;
        }
    }

    public record FinishMetrics(
            long lockWaitCount,
            long deadlockRetryCount,
            long dbRowsBefore,
            long estimatedGapRows) {
    }
}
