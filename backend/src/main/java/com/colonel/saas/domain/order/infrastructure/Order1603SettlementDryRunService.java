package com.colonel.saas.domain.order.infrastructure;

import com.colonel.saas.domain.order.policy.OrderDualTrackAmountResolver;
import com.colonel.saas.service.settlement.SettlementOrderGateway;
import com.colonel.saas.service.settlement.SettlementOrderPage;
import com.colonel.saas.service.settlement.SettlementOrderQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class Order1603SettlementDryRunService {

    public static final String SOURCE = OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT;
    public static final String API_METHOD = "buyin.instituteOrderColonel";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_PAGES = 3;
    private static final int MAX_ALLOWED_PAGES = 10;
    private static final int DEFAULT_MAX_ORDERS = 100;
    private static final int MAX_ALLOWED_ORDERS = 500;
    private static final String DEFAULT_TIME_TYPE = "update";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final SettlementOrderGateway instituteSettlementGateway;

    public Order1603SettlementDryRunService(
            @Qualifier("instituteOrderColonelSettlementGateway") SettlementOrderGateway instituteSettlementGateway) {
        this.instituteSettlementGateway = instituteSettlementGateway;
    }

    public DryRunResult dryRun(DryRunRequest request) {
        DryRunRequest normalized = normalize(request);
        List<OrderMapping> orders = new ArrayList<>();
        Set<String> fieldKeys = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        if (!normalized.safeOrderIds().isEmpty()) {
            warnings.add("order_ids_ignored_by_1603: use time window for dry-run evidence");
        }

        String cursor = normalized.cursor();
        int pagesFetched = 0;
        String stopReason = "UNKNOWN";
        Set<String> seenCursors = new LinkedHashSet<>();
        seenCursors.add(cursor);
        while (pagesFetched < normalized.maxPages() && orders.size() < normalized.maxOrders()) {
            SettlementOrderPage page = instituteSettlementGateway.fetch(new SettlementOrderQuery(
                    normalized.startTime(),
                    normalized.endTime(),
                    normalized.timeType(),
                    normalized.pageSize(),
                    cursor,
                    normalized.safeOrderIds(),
                    normalized.maxPages(),
                    normalized.maxOrders(),
                    false
            ));
            pagesFetched++;
            List<JsonNode> rawOrders = page == null || page.orders() == null ? List.of() : page.orders();
            if (rawOrders.isEmpty()) {
                stopReason = "EMPTY_PAGE";
                break;
            }
            for (JsonNode node : rawOrders) {
                if (orders.size() >= normalized.maxOrders()) {
                    stopReason = "MAX_ORDERS";
                    break;
                }
                Map<String, Object> raw = toMap(node);
                fieldKeys.addAll(fieldKeys(raw));
                OrderMapping mapping = mapOrder(raw);
                orders.add(mapping);
                warnings.addAll(prefixOrderWarnings(mapping.orderId(), mapping.mappingWarnings()));
            }
            if ("MAX_ORDERS".equals(stopReason)) {
                break;
            }
            cursor = normalizeCursor(page == null ? null : page.nextCursor());
            boolean hasNext = page != null
                    && (page.hasMore() || (isTraversableCursor(cursor) && !rawOrders.isEmpty()));
            if (!hasNext) {
                stopReason = "NO_NEXT_CURSOR";
                break;
            }
            if (seenCursors.contains(cursor)) {
                stopReason = "DUPLICATE_CURSOR";
                break;
            }
            seenCursors.add(cursor);
        }
        if ("UNKNOWN".equals(stopReason)) {
            stopReason = "MAX_PAGES";
        }
        return new DryRunResult(
                SOURCE,
                API_METHOD,
                normalized.timeType(),
                orders.size(),
                List.copyOf(orders),
                List.copyOf(fieldKeys),
                mappingConfidence(orders),
                List.copyOf(warnings),
                pagesFetched,
                cursor,
                stopReason,
                false
        );
    }

    private DryRunRequest normalize(DryRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("1603 settlement dry-run request is required");
        }
        if (!StringUtils.hasText(request.startTime()) || !StringUtils.hasText(request.endTime())) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        int pageSize = clamp(request.pageSize(), DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        int maxPages = clamp(request.maxPages(), DEFAULT_MAX_PAGES, 1, MAX_ALLOWED_PAGES);
        int maxOrders = clamp(request.maxOrders(), DEFAULT_MAX_ORDERS, 1, MAX_ALLOWED_ORDERS);
        String timeType = StringUtils.hasText(request.timeType())
                ? request.timeType().trim().toLowerCase()
                : DEFAULT_TIME_TYPE;
        return new DryRunRequest(
                request.startTime().trim(),
                request.endTime().trim(),
                timeType,
                pageSize,
                normalizeCursor(request.cursor()),
                maxPages,
                maxOrders,
                request.safeOrderIds()
        );
    }

    private int clamp(Integer raw, int defaultValue, int min, int max) {
        int value = raw == null || raw <= 0 ? defaultValue : raw;
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeCursor(String cursor) {
        return StringUtils.hasText(cursor) ? cursor.trim() : "0";
    }

    private boolean isTraversableCursor(String cursor) {
        return StringUtils.hasText(cursor) && !"0".equals(cursor);
    }

    private OrderMapping mapOrder(Map<String, Object> raw) {
        OrderDualTrackAmountResolver.DualTrackAmounts amounts =
                OrderDualTrackAmountResolver.resolveInstituteSettlement(raw);
        String orderId = asString(rawValue(raw, "order_id", "orderId", "order_id_str", "orderIdStr"));
        String settleTime = asString(rawValue(raw, "settle_time", "settleTime", "settled_time", "settledTime"));
        String payTime = asString(rawValue(raw, "pay_success_time", "paySuccessTime", "pay_time", "payTime"));
        String flowPoint = asString(rawValue(raw, "flow_point", "flowPoint"));
        String orderStatus = asString(rawValue(raw, "order_status", "orderStatus", "status"));
        List<String> mappingWarnings = new ArrayList<>();
        if (amounts.settleAmount() <= 0) {
            mappingWarnings.add("missing_settle_amount");
        }
        if (amounts.effectiveServiceFee() <= 0) {
            mappingWarnings.add("missing_effective_service_fee");
        }
        if (!StringUtils.hasText(settleTime)) {
            mappingWarnings.add("missing_settle_time");
        }
        if (!StringUtils.hasText(flowPoint)) {
            mappingWarnings.add("missing_flow_point");
        }
        return new OrderMapping(
                orderId,
                amounts.payAmount(),
                amounts.settleAmount(),
                amounts.estimateServiceFee(),
                amounts.effectiveServiceFee(),
                amounts.estimateTechServiceFee(),
                amounts.effectiveTechServiceFee(),
                payTime,
                settleTime,
                flowPoint,
                orderStatus,
                List.copyOf(mappingWarnings)
        );
    }

    private String mappingConfidence(List<OrderMapping> orders) {
        if (orders.isEmpty()) {
            return "LOW";
        }
        long complete = orders.stream()
                .filter(order -> order.settleAmount() > 0)
                .filter(order -> order.effectiveServiceFee() > 0)
                .filter(order -> StringUtils.hasText(order.settleTime()))
                .count();
        if (complete == orders.size()) {
            return "HIGH";
        }
        return complete > 0 ? "MEDIUM" : "LOW";
    }

    private List<String> prefixOrderWarnings(String orderId, List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return List.of();
        }
        String prefix = StringUtils.hasText(orderId) ? orderId : "UNKNOWN_ORDER";
        return warnings.stream()
                .map(warning -> prefix + ":" + warning)
                .toList();
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.convertValue(node, MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    private Set<String> fieldKeys(Map<String, Object> raw) {
        Set<String> keys = new LinkedHashSet<>();
        if (raw == null) {
            return keys;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            keys.add(entry.getKey());
            if (entry.getValue() instanceof Map<?, ?> nested) {
                for (Object nestedKey : nested.keySet()) {
                    keys.add(entry.getKey() + "." + nestedKey);
                }
            }
        }
        return keys;
    }

    private Object rawValue(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        Object orderInfo = source.get("colonel_order_info");
        if (orderInfo instanceof Map<?, ?> nested) {
            Map<String, Object> nestedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                nestedMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            for (String key : keys) {
                if (nestedMap.containsKey(key)) {
                    return nestedMap.get(key);
                }
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record DryRunRequest(
            String startTime,
            String endTime,
            String timeType,
            Integer pageSize,
            String cursor,
            Integer maxPages,
            Integer maxOrders,
            List<String> orderIds) {

        public List<String> safeOrderIds() {
            return orderIds == null ? List.of() : orderIds.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
        }
    }

    public record DryRunResult(
            String source,
            String apiMethod,
            String timeType,
            int fetched,
            List<OrderMapping> orders,
            List<String> fieldKeys,
            String mappingConfidence,
            List<String> mappingWarnings,
            int pagesFetched,
            String nextCursor,
            String stopReason,
            boolean writeEnabled) {
    }

    public record OrderMapping(
            String orderId,
            long payAmount,
            long settleAmount,
            long estimateServiceFee,
            long effectiveServiceFee,
            long estimateTechServiceFee,
            long effectiveTechServiceFee,
            String payTime,
            String settleTime,
            String flowPoint,
            String orderStatus,
            List<String> mappingWarnings) {
    }
}
