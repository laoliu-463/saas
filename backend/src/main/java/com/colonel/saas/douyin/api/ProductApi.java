package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ProductApi {

    private static final int DEFAULT_COUNT = 20;
    private static final int MAX_COUNT = 100;

    private final DouyinApiClient douyinApiClient;

    public ProductApi(DouyinApiClient douyinApiClient) {
        this.douyinApiClient = douyinApiClient;
    }

    public Map<String, Object> listActivities(Long startTime, Long endTime, Integer count, String cursor) {
        Map<String, Object> params = new HashMap<>();
        long now = System.currentTimeMillis() / 1000;
        params.put("start_time", startTime == null ? now - 7 * 24 * 3600 : startTime);
        params.put("end_time", endTime == null ? now : endTime);
        params.put("count", normalizeCount(count));
        params.put("cursor", normalizeCursor(cursor));
        return douyinApiClient.post("buyin.colonel.activity.list", params);
    }

    public Map<String, Object> listByActivity(String activityId) {
        return listProductsByActivity(activityId, null, null);
    }

    public Map<String, Object> listProductsByActivity(String activityId, Integer count, String cursor) {
        Map<String, Object> params = new HashMap<>();
        params.put("activity_id", activityId);
        params.put("count", normalizeCount(count));
        params.put("cursor", normalizeCursor(cursor));
        return douyinApiClient.post("buyin.colonel.product.list", params);
    }

    private int normalizeCount(Integer count) {
        if (count == null || count <= 0) {
            return DEFAULT_COUNT;
        }
        return Math.min(count, MAX_COUNT);
    }

    private long normalizeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }
}
