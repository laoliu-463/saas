package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.time.AppZone;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
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
import java.util.function.Predicate;

@Service
public class Order6468PaginationDryRunService {

    public static final String ALL_RAW = "ALL_RAW";
    public static final String PAY_SUCC = "PAY_SUCC";
    public static final String NON_REFUND_NON_CANCELLED = "NON_REFUND_NON_CANCELLED";
    public static final String PAY_TIME_RANGE = "PAY_TIME_RANGE";
    public static final String CREATE_TIME_RANGE = "CREATE_TIME_RANGE";
    public static final String UPDATE_TIME_RANGE = "UPDATE_TIME_RANGE";
    public static final String NEW_AFTER_LOCAL_DEDUP = "NEW_AFTER_LOCAL_DEDUP";

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_PAGES = 500;
    private static final int MAX_ALLOWED_PAGES = 500;
    private static final int DEFAULT_MAX_ORDERS = 50_000;
    private static final int MAX_ALLOWED_ORDERS = 50_000;
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DouyinOrderGateway douyinOrderGateway;
    private final ColonelsettlementOrderMapper orderMapper;

    public Order6468PaginationDryRunService(
            DouyinOrderGateway douyinOrderGateway,
            ColonelsettlementOrderMapper orderMapper) {
        this.douyinOrderGateway = douyinOrderGateway;
        this.orderMapper = orderMapper;
    }

    public DryRunResult dryRun(DryRunRequest request) {
        DryRunRequest normalized = normalize(request);
        Map<String, OrderProbe> uniqueOrders = new LinkedHashMap<>();
        Set<String> seenCursors = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        String cursor = "0";
        String lastCursor = cursor;
        String stopReason = "UNKNOWN";
        int pagesFetched = 0;
        int rawOrderRows = 0;
        seenCursors.add(cursor);

        while (pagesFetched < normalized.maxPages()) {
            DouyinOrderGateway.OrderListResult page;
            try {
                page = douyinOrderGateway.listInstituteOrders(
                        new DouyinOrderGateway.DouyinOrderQueryRequest(
                                normalized.startTime(),
                                normalized.endTime(),
                                normalized.pageSize(),
                                cursor
                        )
                );
            } catch (RuntimeException ex) {
                stopReason = "UPSTREAM_ERROR";
                warnings.add("upstream error at cursor " + cursor + ": " + ex.getMessage());
                break;
            }
            pagesFetched++;
            List<DouyinOrderGateway.DouyinOrderItem> pageOrders =
                    page.orders() == null ? List.of() : page.orders();
            boolean maxOrdersReached = false;
            for (DouyinOrderGateway.DouyinOrderItem item : pageOrders) {
                if (rawOrderRows >= normalized.maxOrders()) {
                    maxOrdersReached = true;
                    break;
                }
                rawOrderRows++;
                if (!StringUtils.hasText(item.externalOrderId())) {
                    continue;
                }
                uniqueOrders.putIfAbsent(item.externalOrderId(), OrderProbe.from(item));
            }
            String nextCursor = normalizeCursor(page.nextCursor());
            lastCursor = nextCursor;
            if (maxOrdersReached) {
                stopReason = "MAX_ORDERS";
                warnings.add("maxOrders reached: " + normalized.maxOrders());
                break;
            }
            if (pageOrders.isEmpty()) {
                stopReason = "EMPTY_PAGE";
                break;
            }
            boolean hasNext = page.hasMore() || (isTraversableCursor(nextCursor) && !pageOrders.isEmpty());
            if (!hasNext) {
                stopReason = "NO_NEXT_CURSOR";
                break;
            }
            if (pagesFetched >= normalized.maxPages()) {
                stopReason = "MAX_PAGES";
                warnings.add("maxPages reached: " + normalized.maxPages());
                break;
            }
            if (seenCursors.contains(nextCursor)) {
                stopReason = "REPEATED_CURSOR";
                warnings.add("repeated cursor detected: " + nextCursor);
                break;
            }
            seenCursors.add(nextCursor);
            cursor = nextCursor;
        }

        if ("UNKNOWN".equals(stopReason)) {
            stopReason = "MAX_PAGES";
            warnings.add("maxPages reached: " + normalized.maxPages());
        }

        List<OrderProbe> probes = new ArrayList<>(uniqueOrders.values());
        Set<String> existingOrderIds = loadExistingOrderIds(uniqueOrders.keySet());
        Map<String, CandidateSummary> candidates = buildCandidates(probes, existingOrderIds, normalized);
        return new DryRunResult(
                normalized.startTime(),
                normalized.endTime(),
                normalized.filterStartTime(),
                normalized.filterEndTime(),
                normalized.pageSize(),
                pagesFetched,
                rawOrderRows,
                uniqueOrders.size(),
                Math.max(0, rawOrderRows - uniqueOrders.size()),
                stopReason,
                lastCursor,
                true,
                Baseline.defaultBaseline(),
                candidates,
                List.copyOf(warnings)
        );
    }

    private Map<String, CandidateSummary> buildCandidates(
            List<OrderProbe> probes,
            Set<String> existingOrderIds,
            DryRunRequest request) {
        Map<String, CandidateSummary> result = new LinkedHashMap<>();
        result.put(ALL_RAW, summarize(ALL_RAW, "all raw unique orders", probes, probe -> true));
        result.put(PAY_SUCC, summarize(PAY_SUCC, "flow_point/status is PAY_SUCC", probes, OrderProbe::isPaySuccess));
        result.put(NON_REFUND_NON_CANCELLED, summarize(
                NON_REFUND_NON_CANCELLED,
                "exclude refund/cancel/closed orders",
                probes,
                OrderProbe::isNotRefundOrCancelled));
        result.put(PAY_TIME_RANGE, summarize(
                PAY_TIME_RANGE,
                "pay_time in filter range",
                probes,
                probe -> inRange(probe.payTime(), request.filterStartTime(), request.filterEndTime())));
        result.put(CREATE_TIME_RANGE, summarize(
                CREATE_TIME_RANGE,
                "create_time in filter range",
                probes,
                probe -> inRange(probe.createTime(), request.filterStartTime(), request.filterEndTime())));
        result.put(UPDATE_TIME_RANGE, summarize(
                UPDATE_TIME_RANGE,
                "update_time in filter range",
                probes,
                probe -> inRange(probe.updateTime(), request.filterStartTime(), request.filterEndTime())));
        result.put(NEW_AFTER_LOCAL_DEDUP, summarize(
                NEW_AFTER_LOCAL_DEDUP,
                "orders not found in local colonelsettlement_order",
                probes,
                probe -> !existingOrderIds.contains(probe.orderId())));
        return result;
    }

    private CandidateSummary summarize(
            String code,
            String label,
            List<OrderProbe> probes,
            Predicate<OrderProbe> filter) {
        long orderCount = 0L;
        long orderAmountCent = 0L;
        long serviceFeeIncomeCent = 0L;
        long techServiceFeeCent = 0L;
        long settlementOrderCount = 0L;
        long settlementAmountCent = 0L;
        for (OrderProbe probe : probes) {
            if (!filter.test(probe)) {
                continue;
            }
            orderCount++;
            orderAmountCent += probe.orderAmountCent();
            serviceFeeIncomeCent += probe.serviceFeeIncomeCent();
            techServiceFeeCent += probe.techServiceFeeCent();
            if (probe.settlementAmountCent() > 0L || probe.settleTime() > 0L) {
                settlementOrderCount++;
            }
            settlementAmountCent += probe.settlementAmountCent();
        }
        Baseline baseline = Baseline.defaultBaseline();
        Delta delta = new Delta(
                orderCount - baseline.orderCount(),
                orderAmountCent - baseline.orderAmountCent(),
                serviceFeeIncomeCent - baseline.serviceFeeIncomeCent(),
                techServiceFeeCent - baseline.techServiceFeeCent(),
                settlementOrderCount - baseline.settlementOrderCount()
        );
        return new CandidateSummary(
                code,
                label,
                orderCount,
                orderAmountCent,
                serviceFeeIncomeCent,
                techServiceFeeCent,
                settlementOrderCount,
                settlementAmountCent,
                delta
        );
    }

    private Set<String> loadExistingOrderIds(Set<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        List<String> batch = new ArrayList<>(1000);
        for (String orderId : orderIds) {
            batch.add(orderId);
            if (batch.size() == 1000) {
                result.addAll(loadExistingOrderIdBatch(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            result.addAll(loadExistingOrderIdBatch(batch));
        }
        return result;
    }

    private Set<String> loadExistingOrderIdBatch(List<String> orderIds) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.select("order_id")
                .eq("deleted", 0)
                .in("order_id", orderIds);
        List<ColonelsettlementOrder> rows = orderMapper.selectList(wrapper);
        if (rows == null || rows.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (ColonelsettlementOrder row : rows) {
            if (StringUtils.hasText(row.getOrderId())) {
                result.add(row.getOrderId());
            }
        }
        return result;
    }

    private DryRunRequest normalize(DryRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("dry-run request is required");
        }
        if (request.startTime() <= 0L || request.endTime() <= 0L || request.endTime() < request.startTime()) {
            throw new IllegalArgumentException("valid startTime and endTime are required");
        }
        int pageSize = clamp(defaultIfNonPositive(request.pageSize(), DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE);
        int maxPages = clamp(defaultIfNonPositive(request.maxPages(), DEFAULT_MAX_PAGES), 1, MAX_ALLOWED_PAGES);
        int maxOrders = clamp(defaultIfNonPositive(request.maxOrders(), DEFAULT_MAX_ORDERS), 1, MAX_ALLOWED_ORDERS);
        long filterStartTime = request.filterStartTime() > 0L ? request.filterStartTime() : request.startTime();
        long filterEndTime = request.filterEndTime() > 0L ? request.filterEndTime() : request.endTime();
        if (filterEndTime < filterStartTime) {
            throw new IllegalArgumentException("filterEndTime must be greater than or equal to filterStartTime");
        }
        return new DryRunRequest(
                request.startTime(),
                request.endTime(),
                pageSize,
                maxPages,
                maxOrders,
                filterStartTime,
                filterEndTime
        );
    }

    private int defaultIfNonPositive(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static boolean inRange(long epochSecond, long startTime, long endTime) {
        return epochSecond > 0L && epochSecond >= startTime && epochSecond <= endTime;
    }

    private static boolean isTraversableCursor(String cursor) {
        return StringUtils.hasText(cursor) && !"0".equals(cursor);
    }

    private static String normalizeCursor(String cursor) {
        return StringUtils.hasText(cursor) ? cursor.trim() : "0";
    }

    private record OrderProbe(
            String orderId,
            long orderAmountCent,
            long serviceFeeIncomeCent,
            long techServiceFeeCent,
            long settlementAmountCent,
            long payTime,
            long createTime,
            long updateTime,
            long settleTime,
            String statusText,
            Integer orderStatus) {

        static OrderProbe from(DouyinOrderGateway.DouyinOrderItem item) {
            Map<String, Object> raw = item.rawPayload() == null ? Map.of() : item.rawPayload();
            return new OrderProbe(
                    item.externalOrderId(),
                    firstPositive(item.orderAmount(), asLong(pickDeep(raw,
                            "pay_goods_amount", "payGoodsAmount", "order_amount", "orderAmount", "total_amount", "totalAmount"))),
                    firstPositive(item.serviceFee(), asLong(pickDeep(raw,
                            "estimated_commission", "estimatedCommission", "service_fee", "serviceFee"))),
                    asLong(pickDeep(raw,
                            "tech_service_fee", "techServiceFee", "estimate_tech_service_fee", "estimateTechServiceFee")),
                    asLong(pickDeep(raw,
                            "settled_goods_amount", "settledGoodsAmount", "settle_amount", "settleAmount")),
                    firstPositive(asEpochSecond(pickDeep(raw,
                                    "pay_success_time", "paySuccessTime", "pay_time", "payTime")),
                            0L),
                    firstPositive(asEpochSecond(pickDeep(raw,
                                    "create_time", "createTime", "order_create_time", "orderCreateTime")),
                            item.createTime() == null ? 0L : item.createTime()),
                    firstPositive(asEpochSecond(pickDeep(raw,
                                    "update_time", "updateTime")),
                            0L),
                    firstPositive(asEpochSecond(pickDeep(raw,
                                    "settle_time", "settleTime")),
                            item.settleTime() == null ? 0L : item.settleTime()),
                    asString(pickDeep(raw,
                            "flow_point", "flowPoint", "order_status", "orderStatus", "status")),
                    item.orderStatus()
            );
        }

        boolean isPaySuccess() {
            return containsStatus("PAY_SUCC") || containsStatus("PAY_SUCCESS");
        }

        boolean isNotRefundOrCancelled() {
            if (orderStatus != null && orderStatus == 4) {
                return false;
            }
            String normalized = statusText == null ? "" : statusText.toUpperCase(Locale.ROOT);
            return !(normalized.contains("REFUND")
                    || normalized.contains("CANCEL")
                    || normalized.contains("CLOSE"));
        }

        private boolean containsStatus(String expected) {
            return statusText != null && statusText.toUpperCase(Locale.ROOT).contains(expected);
        }
    }

    private static Object pickDeep(Map<String, Object> source, String... keys) {
        Object direct = pick(source, keys);
        if (direct != null) {
            return direct;
        }
        Object nested = pick(source, "colonel_order_info", "colonelOrderInfo");
        if (nested instanceof Map<?, ?> map) {
            Object nestedValue = pick(asStringMap(map), keys);
            if (nestedValue != null) {
                return nestedValue;
            }
        }
        Object secondNested = pick(source, "colonel_order_info_second", "colonelOrderInfoSecond");
        if (secondNested instanceof Map<?, ?> map) {
            return pick(asStringMap(map), keys);
        }
        return null;
    }

    private static Object pick(Map<String, Object> source, String... keys) {
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

    private static Map<String, Object> asStringMap(Map<?, ?> map) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return converted;
    }

    private static long firstPositive(Long first, long fallback) {
        return first != null && first > 0L ? first : Math.max(0L, fallback);
    }

    private static long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return 0L;
        }
        String text = String.valueOf(raw).trim();
        if (!StringUtils.hasText(text)) {
            return 0L;
        }
        try {
            return new BigDecimal(text).longValue();
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String asString(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static long asEpochSecond(Object raw) {
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value > 10_000_000_000L ? value / 1000L : value;
        }
        if (raw == null) {
            return 0L;
        }
        String text = String.valueOf(raw).trim();
        if (!StringUtils.hasText(text)) {
            return 0L;
        }
        try {
            long value = Long.parseLong(text);
            return value > 10_000_000_000L ? value / 1000L : value;
        } catch (NumberFormatException ignored) {
            // Continue with date parsing.
        }
        try {
            return AppZone.toEpochSecond(LocalDateTime.parse(text, LOCAL_DATE_TIME));
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }

    public record DryRunRequest(
            long startTime,
            long endTime,
            int pageSize,
            int maxPages,
            int maxOrders,
            long filterStartTime,
            long filterEndTime) {
    }

    public record DryRunResult(
            long startTime,
            long endTime,
            long filterStartTime,
            long filterEndTime,
            int pageSize,
            int pagesFetched,
            int rawOrderRows,
            int uniqueOrders,
            int duplicateOrders,
            String stopReason,
            String lastCursor,
            boolean readOnly,
            Baseline baseline,
            Map<String, CandidateSummary> candidates,
            List<String> warnings) {
    }

    public record Baseline(
            long orderCount,
            long orderAmountCent,
            long serviceFeeIncomeCent,
            long techServiceFeeCent,
            long settlementOrderCount) {

        public static Baseline defaultBaseline() {
            return new Baseline(3739L, 7_940_007L, 143_401L, 12_136L, 0L);
        }
    }

    public record CandidateSummary(
            String code,
            String label,
            long orderCount,
            long orderAmountCent,
            long serviceFeeIncomeCent,
            long techServiceFeeCent,
            long settlementOrderCount,
            long settlementAmountCent,
            Delta delta) {
    }

    public record Delta(
            long orderCount,
            long orderAmountCent,
            long serviceFeeIncomeCent,
            long techServiceFeeCent,
            long settlementOrderCount) {
    }
}
