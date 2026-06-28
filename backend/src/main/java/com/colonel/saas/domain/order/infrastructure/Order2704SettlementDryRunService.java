package com.colonel.saas.domain.order.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.domain.order.policy.OrderDualTrackAmountResolver;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.settlement.SettlementOrderGateway;
import com.colonel.saas.service.settlement.SettlementOrderPage;
import com.colonel.saas.service.settlement.SettlementOrderQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class Order2704SettlementDryRunService {

    public static final String SOURCE = OrderSyncPersistenceService.SYNC_SOURCE_SETTLEMENT;
    public static final String API_METHOD = "buyin.colonelMultiSettlementOrders";

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_PAGES = 500;
    private static final int MAX_ALLOWED_PAGES = 500;
    private static final int DEFAULT_MAX_ORDERS = 50_000;
    private static final int MAX_ALLOWED_ORDERS = 50_000;
    private static final int DEFAULT_MAX_DIFF_ORDER_IDS = 500;
    private static final int MAX_ALLOWED_DIFF_ORDER_IDS = 5_000;
    private static final String DEFAULT_TIME_TYPE = "settle";
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final SettlementOrderGateway multiSettlementGateway;
    private final ColonelsettlementOrderMapper orderMapper;

    public Order2704SettlementDryRunService(
            @Qualifier("multiSettlementOrderFallbackGateway") SettlementOrderGateway multiSettlementGateway,
            ColonelsettlementOrderMapper orderMapper) {
        this.multiSettlementGateway = multiSettlementGateway;
        this.orderMapper = orderMapper;
    }

    public DryRunResult dryRun(DryRunRequest request) {
        DryRunRequest normalized = normalize(request);
        Map<String, Map<String, Object>> uniqueOrders = new LinkedHashMap<>();
        Set<String> fieldKeys = new LinkedHashSet<>();
        Map<String, Long> fieldSums = new LinkedHashMap<>();
        Set<String> seenCursors = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        String cursor = normalized.cursor();
        String nextCursor = cursor;
        int pagesFetched = 0;
        int rawOrderRows = 0;
        String stopReason = "UNKNOWN";
        seenCursors.add(cursor);

        while (pagesFetched < normalized.maxPages() && rawOrderRows < normalized.maxOrders()) {
            SettlementOrderPage page;
            try {
                page = multiSettlementGateway.fetch(new SettlementOrderQuery(
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
            } catch (RuntimeException ex) {
                stopReason = "UPSTREAM_ERROR";
                warnings.add("upstream error at cursor " + cursor + ": " + ex.getMessage());
                break;
            }
            pagesFetched++;
            List<JsonNode> rawOrders = page == null || page.orders() == null ? List.of() : page.orders();
            if (rawOrders.isEmpty()) {
                stopReason = "EMPTY_PAGE";
                nextCursor = normalizeCursor(page == null ? null : page.nextCursor());
                break;
            }
            for (JsonNode node : rawOrders) {
                if (rawOrderRows >= normalized.maxOrders()) {
                    stopReason = "MAX_ORDERS";
                    warnings.add("maxOrders reached: " + normalized.maxOrders());
                    break;
                }
                rawOrderRows++;
                Map<String, Object> raw = toMap(node);
                collectFieldKeys(raw, "", fieldKeys);
                String orderId = asString(pickDeep(raw, "order_id", "orderId", "order_id_str", "orderIdStr"));
                if (!StringUtils.hasText(orderId) || uniqueOrders.containsKey(orderId)) {
                    continue;
                }
                uniqueOrders.put(orderId, raw);
                collectFieldSums(raw, "", fieldSums);
            }
            if ("MAX_ORDERS".equals(stopReason)) {
                break;
            }
            nextCursor = normalizeCursor(page == null ? null : page.nextCursor());
            boolean hasNext = page != null
                    && (page.hasMore() || (isTraversableCursor(nextCursor) && !rawOrders.isEmpty()));
            if (!hasNext) {
                stopReason = "NO_NEXT_CURSOR";
                break;
            }
            if (seenCursors.contains(nextCursor)) {
                stopReason = "DUPLICATE_CURSOR";
                warnings.add("duplicate cursor detected: " + nextCursor);
                break;
            }
            seenCursors.add(nextCursor);
            cursor = nextCursor;
        }
        if ("UNKNOWN".equals(stopReason)) {
            stopReason = "MAX_PAGES";
            warnings.add("maxPages reached: " + normalized.maxPages());
        }

        DiffSummary diff = compareLocalOrders(uniqueOrders.keySet(), normalized, warnings);
        return new DryRunResult(
                SOURCE,
                API_METHOD,
                normalized.timeType(),
                normalized.startTime(),
                normalized.endTime(),
                normalized.pageSize(),
                pagesFetched,
                rawOrderRows,
                uniqueOrders.size(),
                Math.max(0, rawOrderRows - uniqueOrders.size()),
                stopReason,
                nextCursor,
                true,
                summarize(uniqueOrders.values()),
                Map.copyOf(fieldSums),
                diff,
                List.copyOf(fieldKeys),
                List.copyOf(warnings)
        );
    }

    private DryRunRequest normalize(DryRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("2704 settlement dry-run request is required");
        }
        if (!StringUtils.hasText(request.startTime()) || !StringUtils.hasText(request.endTime())) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        parseDateTime(request.startTime(), "startTime");
        parseDateTime(request.endTime(), "endTime");
        int pageSize = clamp(request.pageSize(), DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        int maxPages = clamp(request.maxPages(), DEFAULT_MAX_PAGES, 1, MAX_ALLOWED_PAGES);
        int maxOrders = clamp(request.maxOrders(), DEFAULT_MAX_ORDERS, 1, MAX_ALLOWED_ORDERS);
        int maxDiffOrderIds = clamp(
                request.maxDiffOrderIds(),
                DEFAULT_MAX_DIFF_ORDER_IDS,
                0,
                MAX_ALLOWED_DIFF_ORDER_IDS);
        String timeType = StringUtils.hasText(request.timeType())
                ? request.timeType().trim().toLowerCase(Locale.ROOT)
                : DEFAULT_TIME_TYPE;
        if (!"settle".equals(timeType) && !"update".equals(timeType)) {
            throw new IllegalArgumentException("timeType must be settle or update");
        }
        return new DryRunRequest(
                request.startTime().trim(),
                request.endTime().trim(),
                timeType,
                pageSize,
                normalizeCursor(request.cursor()),
                maxPages,
                maxOrders,
                maxDiffOrderIds,
                request.safeOrderIds()
        );
    }

    private int clamp(Integer raw, int defaultValue, int min, int max) {
        int value = raw == null || raw < min ? defaultValue : raw;
        return Math.max(min, Math.min(max, value));
    }

    private AmountSummary summarize(Iterable<Map<String, Object>> orders) {
        long settleAmount = 0L;
        long serviceFeeIncome = 0L;
        long techServiceFee = 0L;
        long serviceFeeExpense = 0L;
        for (Map<String, Object> raw : orders) {
            OrderDualTrackAmountResolver.DualTrackAmounts amounts =
                    OrderDualTrackAmountResolver.resolveStrictSettlement(raw, null, null);
            settleAmount += amounts.settleAmount();
            serviceFeeIncome += amounts.effectiveServiceFee();
            techServiceFee += amounts.effectiveTechServiceFee();
            serviceFeeExpense += amounts.effectiveServiceFeeExpense();
        }
        return new AmountSummary(settleAmount, serviceFeeIncome, techServiceFee, serviceFeeExpense);
    }

    private DiffSummary compareLocalOrders(
            Set<String> upstreamOrderIds,
            DryRunRequest request,
            List<String> warnings) {
        Set<String> localOrderIds = loadLocalOrderIds(request, warnings);
        List<String> onlyInUpstream = difference(upstreamOrderIds, localOrderIds, request.maxDiffOrderIds());
        List<String> onlyInLocal = difference(localOrderIds, upstreamOrderIds, request.maxDiffOrderIds());
        long onlyInUpstreamCount = upstreamOrderIds.stream().filter(id -> !localOrderIds.contains(id)).count();
        long onlyInLocalCount = localOrderIds.stream().filter(id -> !upstreamOrderIds.contains(id)).count();
        return new DiffSummary(
                localOrderIds.size(),
                onlyInUpstreamCount,
                onlyInLocalCount,
                onlyInUpstream,
                onlyInLocal);
    }

    private Set<String> loadLocalOrderIds(DryRunRequest request, List<String> warnings) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.select("order_id")
                .eq("deleted", 0)
                .ge("settle_time", parseDateTime(request.startTime(), "startTime"))
                .lt("settle_time", parseDateTime(request.endTime(), "endTime"));
        List<ColonelsettlementOrder> rows;
        try {
            rows = orderMapper.selectList(wrapper);
        } catch (RuntimeException ex) {
            warnings.add("local diff skipped: " + ex.getMessage());
            return Set.of();
        }
        if (rows == null || rows.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (ColonelsettlementOrder row : rows) {
            if (row != null && StringUtils.hasText(row.getOrderId())) {
                result.add(row.getOrderId());
            }
        }
        return result;
    }

    private List<String> difference(Set<String> source, Set<String> target, int limit) {
        if (source == null || source.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : source) {
            if (!target.contains(value)) {
                result.add(value);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.convertValue(node, MAP_TYPE);
    }

    private void collectFieldKeys(Map<String, Object> raw, String prefix, Set<String> keys) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String path = path(prefix, entry.getKey());
            keys.add(path);
            if (entry.getValue() instanceof Map<?, ?> nested) {
                collectFieldKeys(asStringMap(nested), path, keys);
            }
        }
    }

    private void collectFieldSums(Map<String, Object> raw, String prefix, Map<String, Long> sums) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String path = path(prefix, entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                collectFieldSums(asStringMap(nested), path, sums);
                continue;
            }
            Long numeric = asRelevantLong(path, value);
            if (numeric != null) {
                sums.merge(path, numeric, Long::sum);
            }
        }
    }

    private Long asRelevantLong(String path, Object raw) {
        if (!isRelevantMoneyPath(path)) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text).longValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isRelevantMoneyPath(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return normalized.contains("amount")
                || normalized.contains("fee")
                || normalized.contains("commission")
                || normalized.contains("expense")
                || normalized.contains("cost")
                || normalized.contains("deduct")
                || normalized.contains("adjust")
                || normalized.contains("subsidy")
                || normalized.contains("service")
                || normalized.contains("pay")
                || normalized.contains("settle")
                || normalized.contains("tech")
                || normalized.contains("real")
                || normalized.contains("estimated");
    }

    private String path(String prefix, String key) {
        return StringUtils.hasText(prefix) ? prefix + "." + key : key;
    }

    private Object pickDeep(Map<String, Object> source, String... keys) {
        Object direct = pick(source, keys);
        if (direct != null) {
            return direct;
        }
        Object nested = pick(source, "colonel_order_info", "colonelOrderInfo");
        if (nested instanceof Map<?, ?> nestedMap) {
            Object value = pick(asStringMap(nestedMap), keys);
            if (value != null) {
                return value;
            }
        }
        Object secondNested = pick(source, "colonel_order_info_second", "colonelOrderInfoSecond");
        if (secondNested instanceof Map<?, ?> secondMap) {
            return pick(asStringMap(secondMap), keys);
        }
        return null;
    }

    private Object pick(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        for (String key : keys) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (key.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private Map<String, Object> asStringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private String normalizeCursor(String cursor) {
        return StringUtils.hasText(cursor) ? cursor.trim() : "0";
    }

    private boolean isTraversableCursor(String cursor) {
        return StringUtils.hasText(cursor) && !"0".equals(cursor);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime parseDateTime(String text, String field) {
        try {
            return LocalDateTime.parse(text.trim(), LOCAL_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must use yyyy-MM-dd HH:mm:ss", ex);
        }
    }

    public record DryRunRequest(
            String startTime,
            String endTime,
            String timeType,
            Integer pageSize,
            String cursor,
            Integer maxPages,
            Integer maxOrders,
            Integer maxDiffOrderIds,
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
            String startTime,
            String endTime,
            int pageSize,
            int pagesFetched,
            int rawOrderRows,
            int uniqueOrders,
            int duplicateOrders,
            String stopReason,
            String nextCursor,
            boolean readOnly,
            AmountSummary summary,
            Map<String, Long> fieldSums,
            DiffSummary diff,
            List<String> fieldKeys,
            List<String> warnings) {
    }

    public record AmountSummary(
            long settleAmountCent,
            long serviceFeeIncomeCent,
            long techServiceFeeCent,
            long serviceFeeExpenseCent) {
    }

    public record DiffSummary(
            long localOrders,
            long onlyInUpstream,
            long onlyInLocal,
            List<String> onlyInUpstreamOrderIds,
            List<String> onlyInLocalOrderIds) {
    }
}
