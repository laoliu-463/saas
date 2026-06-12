package com.colonel.saas.douyin.api;

import com.colonel.saas.common.time.AppZone;
import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 精选联盟订单结算 API 客户端。
 * <p>
 * 封装抖音精选联盟订单结算查询接口，支持滑动窗口查询和多订单批量查询，
 * 支持 contract（合同模式）与真实上游两种调用路径。
 *
 * <ul>
 *   <li>结算订单列表 — 按时间范围分页查询结算订单</li>
 *   <li>滑动窗口查询 — 基于最近时间窗口快速拉取最新订单</li>
 *   <li>多订单批量查询 — 支持按订单 ID 列表或时间范围批量查询结算订单</li>
 * </ul>
 *
 * 所属业务领域：精选联盟 / 订单结算
 *
 * @see DouyinApiClient
 * @see DouyinUpstreamModeSupport
 * @see DouyinContractFixtureProvider
 */
@Slf4j
@Service
public class OrderApi {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    /** 订单列表查询接口方法名 */
    private static final String INSTITUTE_ORDER_COLONEL_METHOD = "buyin.instituteOrderColonel";
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

    /**
     * 按时间范围查询结算订单列表。
     * <p>
     * 在 contract 模式下返回契约桩数据，否则调用真实上游 API。
     *
     * @param startTime 开始时间（epoch 秒）
     * @param endTime   结束时间（epoch 秒）
     * @param count     每页数量，范围 1~100，默认 100
     * @param cursor    分页游标（首次查询传 null 或 "0"）
     * @return 订单列表响应，包含 order_list、has_more、next_cursor 字段
     */
    public Map<String, Object> listSettlement(long startTime, long endTime, int count, String cursor) {
        JsonNode response = listInstituteOrderColonelForSettlement(
                formatEpochSecond(startTime),
                formatEpochSecond(endTime),
                "update",
                count,
                cursor,
                List.of());
        return OBJECT_MAPPER.convertValue(response, MAP_TYPE);
    }

    /**
     * 1603 团长订单结算口径查询。
     * <p>
     * 该方法是结算写库主链路的 1603 专用入口。官方 1603 对 {@code order_ids}
     * 支持尚未确认，因此当前只接收该参数用于调用方语义表达，不强传给上游。
     */
    public JsonNode listInstituteOrderColonelForSettlement(
            String startTime,
            String endTime,
            String timeType,
            Integer size,
            String cursor,
            List<String> orderIds) {
        if (orderIds != null && !orderIds.isEmpty()) {
            log.warn("api_method={} order_ids_ignored=true reason=1603_not_confirmed_support_order_ids count={}",
                    INSTITUTE_ORDER_COLONEL_METHOD, orderIds.size());
        }
        if (upstreamModeSupport.isContract()) {
            Map<String, Object> response = contractFixtureProvider.buildOrderSettlementResponse(
                    null,
                    size,
                    cursor,
                    hasText(timeType) ? timeType : "settle",
                    startTime,
                    endTime,
                    null);
            return OBJECT_MAPPER.valueToTree(response);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        if (hasText(startTime)) {
            params.put("start_time", startTime.trim());
        }
        if (hasText(endTime)) {
            params.put("end_time", endTime.trim());
        }
        params.put("size", normalizeCount(size == null ? DEFAULT_COUNT : size));
        params.put("cursor", hasText(cursor) ? cursor.trim() : "0");
        if (hasText(timeType)) {
            params.put("time_type", normalizeTimeType(timeType));
        }
        return OBJECT_MAPPER.valueToTree(douyinApiClient.post(INSTITUTE_ORDER_COLONEL_METHOD, params));
    }

    /**
     * 基于滑动窗口查询最近结算订单。
     * <p>
     * 自动计算最近 {@value DEFAULT_WINDOW_SECONDS} 秒 + {@value DEFAULT_OVERLAP_SECONDS} 秒重叠的时间范围，
     * 用于增量拉取最新订单。
     *
     * @param cursor 分页游标
     * @param count  每页数量（可为空，默认 100）
     * @return 订单列表响应
     */
    public Map<String, Object> listSettlementWindow(String cursor, Integer count) {
        long endTime = System.currentTimeMillis() / 1000;
        long startTime = endTime - DEFAULT_WINDOW_SECONDS - DEFAULT_OVERLAP_SECONDS;
        return listSettlement(startTime, endTime, count == null ? DEFAULT_COUNT : count, cursor);
    }

    /**
     * 批量查询多订单结算详情。
     * <p>
     * 仅作为 fallback/probe/对照，不再作为默认结算写库主链路。
     * <p>
     * 支持两种查询模式：按时间范围查询或按订单 ID 列表查询，两者至少提供其一。
     * 在 contract 模式下返回契约桩数据，否则调用真实上游 API。
     *
     * @param appId     应用 ID（可为空）
     * @param size      每页数量，范围 1~100，默认 50
     * @param cursor    分页游标（数字字符串，默认 "0"）
     * @param timeType  时间类型：settle-结算时间，update-更新时间，默认 update
     * @param startTime 开始时间（格式：yyyy-MM-dd HH:mm:ss，可为空）
     * @param endTime   结束时间（格式：yyyy-MM-dd HH:mm:ss，可为空）
     * @param orderIds  订单 ID 列表（逗号分隔，最多 100 个）
     * @return 多订单结算响应
     * @throws IllegalArgumentException 当参数校验不通过时抛出
     */
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

    /**
     * 校验多订单批量查询参数的合法性。
     * <p>
     * 校验规则：时间范围和订单 ID 至少提供一个；时间起止必须成对提供；
     * 开始时间不得晚于结束时间；时间范围不超过 90 天。
     *
     * @param orderIds  逗号分隔的订单 ID 列表
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @throws IllegalArgumentException 当参数校验不通过时抛出
     */
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
