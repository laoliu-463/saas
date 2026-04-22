package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.DouyinApiException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OrderApi {

    private static final String ORDER_LIST_METHOD = "buyin.instituteOrderColonel";
    private static final String LEGACY_DECRYPT_METHOD = "order.batchSensitiveDataRequest";
    private static final String NEW_DECRYPT_METHOD = "order.batchSensitive";
    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_COUNT = 100;
    private static final long DEFAULT_WINDOW_SECONDS = 600L;
    private static final long DEFAULT_OVERLAP_SECONDS = 60L;

    private final DouyinApiClient douyinApiClient;

    public OrderApi(DouyinApiClient douyinApiClient) {
        this.douyinApiClient = douyinApiClient;
    }

    public Map<String, Object> listSettlement(long startTime, long endTime, int count, String cursor) {
        int normalizedCount = normalizeCount(count);
        long normalizedCursor = normalizeCursor(cursor);
        long page = normalizedCursor + 1;

        Map<String, Object> params = new HashMap<>();
        params.put("start_time", startTime);
        params.put("end_time", endTime);
        params.put("page", page);
        params.put("count", normalizedCount);

        try {
            return douyinApiClient.post(ORDER_LIST_METHOD, params);
        } catch (DouyinApiException ex) {
            if (!isParameterInvalid(ex)) {
                throw ex;
            }
            Map<String, Object> retryParams = new HashMap<>();
            retryParams.put("start_time", startTime);
            retryParams.put("end_time", endTime);
            retryParams.put("cursor", normalizedCursor);
            retryParams.put("count", normalizedCount);
            return douyinApiClient.post(ORDER_LIST_METHOD, retryParams);
        }
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
        try {
            return douyinApiClient.post(LEGACY_DECRYPT_METHOD, params);
        } catch (DouyinApiException ex) {
            if (!isApiServiceOff(ex)) {
                throw ex;
            }
            Map<String, Object> fallbackParams = new HashMap<>();
            fallbackParams.put("cipher_infos", orderIds);
            return douyinApiClient.post(NEW_DECRYPT_METHOD, fallbackParams);
        }
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

    private boolean isParameterInvalid(DouyinApiException ex) {
        if (ex == null) {
            return false;
        }
        String subCode = ex.getSubCode();
        String message = ex.getErrorMsg();
        return containsIgnoreCase(subCode, "parameter-invalid")
                || containsIgnoreCase(subCode, "business-failed:257")
                || containsIgnoreCase(message, "参数校验失败");
    }

    private boolean isApiServiceOff(DouyinApiException ex) {
        if (ex == null) {
            return false;
        }
        String subCode = ex.getSubCode();
        String message = ex.getErrorMsg();
        return ex.getErrorCode() == 70000
                || containsIgnoreCase(subCode, "api-service-off")
                || containsIgnoreCase(message, "API不存在")
                || containsIgnoreCase(message, "API已下线");
    }

    private boolean containsIgnoreCase(String source, String needle) {
        if (source == null || needle == null) {
            return false;
        }
        return source.toLowerCase().contains(needle.toLowerCase());
    }
}
