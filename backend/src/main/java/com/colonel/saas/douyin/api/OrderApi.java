package com.colonel.saas.douyin.api;

import com.colonel.saas.common.time.AppZone;
import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderApi {

    private static final String ORDER_LIST_METHOD = "buyin.instituteOrderColonel";
    private static final String COLONEL_MULTI_SETTLEMENT_METHOD = "buyin.colonelMultiSettlementOrders";
    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_COUNT = 100;
    private static final int DEFAULT_MULTI_SETTLEMENT_SIZE = 50;
    private static final String DEFAULT_MULTI_SETTLEMENT_CURSOR = "0";
    private static final String DEFAULT_MULTI_SETTLEMENT_TIME_TYPE = "update";
    private static final long MAX_MULTI_SETTLEMENT_RANGE_DAYS = 90L;
    private static final long DEFAULT_WINDOW_SECONDS = 600L;
    private static final long DEFAULT_OVERLAP_SECONDS = 60L;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DouyinApiClient douyinApiClient;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public OrderApi(
            DouyinApiClient douyinApiClient,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.douyinApiClient = douyinApiClient;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    public Map<String, Object> listSettlement(long startTime, long endTime, int count, String cursor) {
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildOrderSettlementResponse(null, count, cursor, "update", null, null, null);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("start_time", formatEpochSecond(startTime));
        params.put("end_time", formatEpochSecond(endTime));
        params.put("size", normalizeCount(count));
        params.put("cursor", hasText(cursor) ? cursor.trim() : "0");
        return douyinApiClient.post(ORDER_LIST_METHOD, params);
    }

    public Map<String, Object> listSettlementWindow(String cursor, Integer count) {
        long endTime = System.currentTimeMillis() / 1000;
        long startTime = endTime - DEFAULT_WINDOW_SECONDS - DEFAULT_OVERLAP_SECONDS;
        return listSettlement(startTime, endTime, count == null ? DEFAULT_COUNT : count, cursor);
    }

    public Map<String, Object> listColonelMultiSettlementOrders(
            String appId,
            Integer size,
            String cursor,
            String timeType,
            String startTime,
            String endTime,
            String orderIds) {
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildOrderSettlementResponse(appId, size, cursor, timeType, startTime, endTime, orderIds);
        }
        String normalizedOrderIds = normalizeOrderIds(orderIds);
        LocalDateTime normalizedStartTime = parseDateTime(startTime, "startTime");
        LocalDateTime normalizedEndTime = parseDateTime(endTime, "endTime");
        validateMultiSettlementQuery(normalizedOrderIds, normalizedStartTime, normalizedEndTime);

        Map<String, Object> params = new HashMap<>();
        if (hasText(appId)) {
            params.put("appId", appId.trim());
        }
        params.put("size", normalizeMultiSettlementSize(size));
        params.put("cursor", normalizeMultiSettlementCursor(cursor));
        params.put("time_type", normalizeTimeType(timeType));
        if (normalizedStartTime != null) {
            params.put("start_time", DATE_TIME_FORMATTER.format(normalizedStartTime));
        }
        if (normalizedEndTime != null) {
            params.put("end_time", DATE_TIME_FORMATTER.format(normalizedEndTime));
        }
        if (normalizedOrderIds != null) {
            params.put("order_ids", normalizedOrderIds);
        }
        return douyinApiClient.post(COLONEL_MULTI_SETTLEMENT_METHOD, params);
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

    private int normalizeMultiSettlementSize(Integer size) {
        if (size == null) {
            return DEFAULT_MULTI_SETTLEMENT_SIZE;
        }
        if (size <= 0 || size > MAX_COUNT) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        return size;
    }

    private String normalizeMultiSettlementCursor(String cursor) {
        String normalizedCursor = hasText(cursor) ? cursor.trim() : DEFAULT_MULTI_SETTLEMENT_CURSOR;
        try {
            if (Long.parseLong(normalizedCursor) < 0) {
                throw new IllegalArgumentException("cursor must be greater than or equal to 0");
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("cursor must be a numeric string", ex);
        }
        return normalizedCursor;
    }

    private String normalizeTimeType(String timeType) {
        if (!hasText(timeType)) {
            return DEFAULT_MULTI_SETTLEMENT_TIME_TYPE;
        }
        String normalized = timeType.trim().toLowerCase();
        if (!"settle".equals(normalized) && !"update".equals(normalized)) {
            throw new IllegalArgumentException("timeType must be settle or update");
        }
        return normalized;
    }

    private String normalizeOrderIds(String orderIds) {
        if (!hasText(orderIds)) {
            return null;
        }
        String[] parts = orderIds.split(",");
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String part : parts) {
            if (!hasText(part)) {
                continue;
            }
            if (count > 0) {
                builder.append(',');
            }
            builder.append(part.trim());
            count++;
        }
        if (count == 0) {
            throw new IllegalArgumentException("orderIds must contain at least one order id");
        }
        if (count > 100) {
            throw new IllegalArgumentException("orderIds must contain at most 100 order ids");
        }
        return builder.toString();
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(fieldName + " must use format yyyy-MM-dd HH:mm:ss", ex);
        }
    }

    private void validateMultiSettlementQuery(
            String orderIds,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        if (orderIds == null && startTime == null && endTime == null) {
            throw new IllegalArgumentException("time range or orderIds is required");
        }
        if (startTime == null ^ endTime == null) {
            throw new IllegalArgumentException("startTime and endTime must be provided together");
        }
        if (startTime != null && endTime != null) {
            if (startTime.isAfter(endTime)) {
                throw new IllegalArgumentException("startTime must be earlier than or equal to endTime");
            }
            long days = Duration.between(startTime, endTime).toDays();
            if (days > MAX_MULTI_SETTLEMENT_RANGE_DAYS) {
                throw new IllegalArgumentException("time range must not exceed 90 days");
            }
        }
    }

    private String formatEpochSecond(long epochSecond) {
        return DATE_TIME_FORMATTER.format(AppZone.fromEpochSecond(epochSecond));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
