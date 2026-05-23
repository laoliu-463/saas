package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.OrderApi;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.service.AttributionSourceNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinOrderGateway implements DouyinOrderGateway {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderApi orderApi;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public RealDouyinOrderGateway(
            OrderApi orderApi,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.orderApi = orderApi;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    @Override
    public OrderListResult listSettlement(DouyinOrderQueryRequest request) {
        logGateway();
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildOrderListResult(request);
        }
        Map<String, Object> response = orderApi.listColonelMultiSettlementOrders(
                null,
                request.count(),
                normalizeCursor(request.cursor()),
                "update",
                formatEpochSecond(request.startTime()),
                formatEpochSecond(request.endTime()),
                null
        );
        return toOrderListResult(response);
    }

    @Override
    public OrderListResult listInstituteOrders(DouyinOrderQueryRequest request) {
        logGateway();
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildOrderListResult(request);
        }
        Map<String, Object> response = orderApi.listSettlement(
                request.startTime(),
                request.endTime(),
                request.count(),
                request.cursor()
        );
        return toOrderListResult(response);
    }

    @Override
    public OrderListResult listSettlementWindow(String cursor, Integer count) {
        logGateway();
        if (upstreamModeSupport.isContract()) {
            long endTime = System.currentTimeMillis() / 1000;
            long startTime = endTime - 3600;
            return contractFixtureProvider.buildOrderListResult(
                    new DouyinOrderQueryRequest(startTime, endTime, count == null ? 100 : count, cursor)
            );
        }
        long endTime = System.currentTimeMillis() / 1000;
        long startTime = endTime - 3600;
        return listSettlement(new DouyinOrderQueryRequest(startTime, endTime, count == null ? 100 : count, cursor));
    }

    @Override
    public OrderListResult listSettlementByOrderIds(List<String> orderIds) {
        logGateway();
        List<String> normalized = normalizeOrderIds(orderIds);
        if (normalized.isEmpty()) {
            return new OrderListResult(List.of(), false, "0", Map.of("order_ids", ""));
        }
        if (upstreamModeSupport.isContract()) {
            return toOrderListResult(contractFixtureProvider.buildOrderSettlementResponse(
                    null,
                    normalized.size(),
                    "0",
                    "update",
                    null,
                    null,
                    String.join(",", normalized)
            ));
        }
        Map<String, Object> response = orderApi.listColonelMultiSettlementOrders(
                null,
                normalized.size(),
                "0",
                "update",
                null,
                null,
                String.join(",", normalized)
        );
        return toOrderListResult(response);
    }

    private void logGateway() {
        log.info(
                "gateway=RealDouyinOrderGateway, upstreamMode={}, appKey={}, shopId={}, authId={}",
                upstreamModeSupport.value(),
                mask(contractFixtureProvider.appKey()),
                contractFixtureProvider.shopId(),
                contractFixtureProvider.authId()
        );
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= 8) {
            return normalized;
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }

    private OrderListResult toOrderListResult(Map<String, Object> response) {
        Map<String, Object> data = asMap(response == null ? null : response.get("data"));
        List<Map<String, Object>> rows = extractOrderRows(data);
        Map<String, Object> pageData = pageData(data);
        List<DouyinOrderItem> orders = rows.stream()
                .map(this::toOrderItem)
                .filter(item -> StringUtils.hasText(item.externalOrderId()))
                .toList();
        return new OrderListResult(
                orders,
                asBoolean(pick(pageData, "has_more", "hasMore", "has_next", "hasNext")),
                firstNonBlank(
                        asString(pick(pageData, "next_cursor", "nextCursor", "cursor", "next_page", "nextPage")),
                        "0"
                ),
                response
        );
    }

    private Map<String, Object> pageData(Map<String, Object> data) {
        Map<String, Object> nested = asMap(data == null ? null : data.get("data"));
        if (nested.isEmpty()) {
            return data == null ? Map.of() : data;
        }
        Map<String, Object> result = new LinkedHashMap<>(data);
        result.putAll(nested);
        return result;
    }

    private List<Map<String, Object>> extractOrderRows(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        Object rows = pick(data, "order_list", "orderList", "orders", "list", "data");
        if (rows instanceof List<?> list) {
            return convertListRows(list);
        }
        if (rows instanceof Map<?, ?> nestedMap) {
            Object nestedRows = pick(asMap(nestedMap), "order_list", "orderList", "orders", "list", "data");
            if (nestedRows instanceof List<?> nestedList) {
                return convertListRows(nestedList);
            }
        }
        return List.of();
    }

    private List<Map<String, Object>> convertListRows(List<?> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows) {
            Map<String, Object> converted = asMap(row);
            if (!converted.isEmpty()) {
                result.add(converted);
            }
        }
        return result;
    }

    private DouyinOrderItem toOrderItem(Map<String, Object> raw) {
        raw = AttributionSourceNormalizer.normalize(raw);
        return new DouyinOrderItem(
                asString(pick(raw, "order_id", "orderId", "order_id_str", "orderIdStr")),
                asString(pick(raw, "external_product_id", "externalProductId", "product_id", "productId")),
                asString(pick(raw, "product_id", "productId")),
                asString(pick(raw, "merchant_id", "merchantId", "shop_id", "shopId")),
                asString(pick(raw, "merchant_name", "merchantName", "shop_name", "shopName")),
                asString(pick(raw, "talent_id", "talentId", "talent_uid", "talentUid", "author_id", "authorId", "author_buyin_id", "authorBuyinId")),
                asString(pick(raw, "talent_name", "talentName", "author_name", "authorName", "author_account", "authorAccount")),
                asString(pick(raw, "pick_source", "pickSource")),
                asLongObject(pick(raw, "order_amount", "orderAmount", "total_amount", "totalAmount", "pay_amount", "payAmount", "total_pay_amount", "totalPayAmount", "pay_goods_amount", "payGoodsAmount")),
                resolveServiceFee(raw),
                asInteger(pick(raw, "order_status", "orderStatus", "status", "flow_point", "flowPoint")),
                firstNonNull(
                        asEpochSecond(pick(raw, "create_time", "createTime", "order_create_time", "orderCreateTime", "pay_success_time", "paySuccessTime")),
                        asEpochSecond(pick(raw, "settle_time", "settleTime", "update_time", "updateTime")),
                        Instant.now().getEpochSecond()
                ),
                asEpochSecond(pick(raw, "settle_time", "settleTime", "update_time", "updateTime")),
                raw
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private Object pick(Map<String, Object> source, String... keys) {
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String formatEpochSecond(long epochSecond) {
        return DATE_TIME_FORMATTER.format(com.colonel.saas.common.time.AppZone.fromEpochSecond(epochSecond));
    }

    private String normalizeCursor(String cursor) {
        return StringUtils.hasText(cursor) ? cursor.trim() : "0";
    }

    private List<String> normalizeOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String orderId : orderIds) {
            if (StringUtils.hasText(orderId)) {
                deduped.add(orderId.trim());
            }
        }
        return List.copyOf(deduped);
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private Long asLongObject(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof String text) {
            return switch (text.trim()) {
                case "PAY_SUCC" -> 1;
                case "REFUND", "REFUND_SUCCESS", "CLOSED" -> 4;
                default -> {
                    Long parsed = asLongObject(text);
                    yield parsed == null ? null : parsed.intValue();
                }
            };
        }
        Long longValue = asLongObject(value);
        return longValue == null ? null : longValue.intValue();
    }

    private Long asEpochSecond(Object value) {
        Long longValue = asLongObject(value);
        if (longValue != null) {
            return longValue > 9_999_999_999L ? longValue / 1000L : longValue;
        }
        if (value == null) {
            return null;
        }
        try {
            return com.colonel.saas.common.time.AppZone.toEpochSecond(
                    LocalDateTime.parse(String.valueOf(value).trim(), DATE_TIME_FORMATTER));
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long resolveServiceFee(Map<String, Object> raw) {
        Long direct = asLongObject(pick(raw, "settle_colonel_commission", "settleColonelCommission", "colonel_commission", "colonelCommission", "service_fee", "serviceFee"));
        if (direct != null) {
            return direct;
        }
        Map<String, Object> colonelInfo = asMap(pick(raw, "colonel_order_info", "colonelOrderInfo"));
        return asLongObject(pick(colonelInfo, "estimated_commission", "estimatedCommission", "real_commission", "realCommission"));
    }
}
