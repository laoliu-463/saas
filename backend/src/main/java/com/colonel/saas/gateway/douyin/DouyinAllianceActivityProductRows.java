package com.colonel.saas.gateway.douyin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared parsing for {@code alliance.colonelActivityProduct} list payloads.
 */
public final class DouyinAllianceActivityProductRows {

    private DouyinAllianceActivityProductRows() {
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extract(Map<String, Object> dataNode) {
        if (dataNode == null || dataNode.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = castListMap(dataNode.get("data"));
        if (rows.isEmpty()) {
            rows = castListMap(dataNode.get("list"));
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castListMap(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object element : iterable) {
            if (element instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }
}
