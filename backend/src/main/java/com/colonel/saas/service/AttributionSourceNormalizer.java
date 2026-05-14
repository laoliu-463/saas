package com.colonel.saas.service;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AttributionSourceNormalizer {

    private AttributionSourceNormalizer() {
    }

    public static Map<String, Object> normalize(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>(source);
        flattenColonelInfo(
                normalized,
                asMap(source.get("colonel_order_info")),
                "colonel_buyin_id",
                "colonel_activity_id"
        );
        flattenColonelInfo(
                normalized,
                asMap(source.get("colonel_order_info_second")),
                "second_colonel_buyin_id",
                "second_colonel_activity_id"
        );
        flattenColonelInfo(
                normalized,
                asMap(source.get("colonelOrderInfo")),
                "colonel_buyin_id",
                "colonel_activity_id"
        );
        flattenColonelInfo(
                normalized,
                asMap(source.get("colonelOrderInfoSecond")),
                "second_colonel_buyin_id",
                "second_colonel_activity_id"
        );
        return normalized;
    }

    private static void flattenColonelInfo(
            Map<String, Object> target,
            Map<String, Object> colonelInfo,
            String colonelBuyinKey,
            String activityKey) {
        if (colonelInfo.isEmpty()) {
            return;
        }
        putIfMissing(target, colonelBuyinKey, pick(colonelInfo, "colonel_buyin_id", "colonelBuyinId"));
        putIfMissing(target, activityKey, pick(colonelInfo, "activity_id", "activityId"));
    }

    private static void putIfMissing(Map<String, Object> target, String key, Object value) {
        if (!StringUtils.hasText(key) || value == null) {
            return;
        }
        Object existing = target.get(key);
        if (!StringUtils.hasText(existing == null ? null : String.valueOf(existing))) {
            target.put(key, value);
        }
    }

    private static Object pick(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return converted;
    }
}
