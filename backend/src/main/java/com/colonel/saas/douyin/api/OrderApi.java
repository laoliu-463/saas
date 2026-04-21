package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OrderApi {

    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_COUNT = 100;
    private static final long DEFAULT_WINDOW_SECONDS = 600L;
    private static final long DEFAULT_OVERLAP_SECONDS = 60L;

    private final DouyinApiClient douyinApiClient;

    public OrderApi(DouyinApiClient douyinApiClient) {
        this.douyinApiClient = douyinApiClient;
    }

    public Map<String, Object> listSettlement(long startTime, long endTime, int count, String cursor) {
        Map<String, Object> params = new HashMap<>();
        params.put("start_time", startTime);
        params.put("end_time", endTime);
        params.put("count", normalizeCount(count));
        params.put("cursor", normalizeCursor(cursor));
        return douyinApiClient.post("buyin.settlement.order.list", params);
    }

    public Map<String, Object> listSettlementWindow(String cursor, Integer count) {
        long endTime = System.currentTimeMillis() / 1000;
        long startTime = endTime - DEFAULT_WINDOW_SECONDS - DEFAULT_OVERLAP_SECONDS;
        return listSettlement(startTime, endTime, count == null ? DEFAULT_COUNT : count, cursor);
    }

    public Map<String, Object> decryptSensitiveData(java.util.List<String> orderIds) {
        Map<String, Object> params = new HashMap<>();
        params.put("order_ids", orderIds);
        params.put("type", 1);
        return douyinApiClient.post("order.batchSensitiveDataRequest", params);
    }

    private int normalizeCount(int count) {
        if (count <= 0) {
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
