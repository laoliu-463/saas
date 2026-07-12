package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.OrderCommissionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link OrderReadFacade} 遗留实现：委派现有 {@link ColonelsettlementOrderMapper}，零行为变更。
 */
@Service
public class LegacyOrderReadFacade implements OrderReadFacade {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 2000;
    private static final String STATUS_ATTRIBUTED = "ATTRIBUTED";
    private static final String STATUS_UNATTRIBUTED = "UNATTRIBUTED";

    private final ColonelsettlementOrderMapper orderMapper;

    public LegacyOrderReadFacade(ColonelsettlementOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public ColonelsettlementOrder findByOrderId(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            return null;
        }
        return orderMapper.findByOrderId(orderId.trim());
    }

    @Override
    public boolean existsActiveByOrderId(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            return false;
        }
        return orderMapper.selectOne(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getOrderId, orderId.trim())
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .last("LIMIT 1")) != null;
    }

    @Override
    public List<ColonelsettlementOrder> findByOrderIds(Collection<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = orderIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .in(ColonelsettlementOrder::getOrderId, normalized)
                .eq(ColonelsettlementOrder::getDeleted, 0));
    }

    @Override
    public List<ColonelsettlementOrder> findInvalidatedOrdersWithStalePerformance(int limit) {
        int safeLimit = normalizeLimit(limit);
        return orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .in(ColonelsettlementOrder::getOrderStatus,
                        OrderCommissionPolicy.STATUS_CANCELLED,
                        OrderCommissionPolicy.STATUS_REFUNDED)
                .apply("""
                        EXISTS (
                            SELECT 1 FROM performance_records pr
                            WHERE pr.order_id = colonelsettlement_order.order_id
                              AND pr.is_valid = TRUE
                        )
                        """)
                .orderByDesc(ColonelsettlementOrder::getUpdateTime)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public List<ColonelsettlementOrder> findOrdersForBackfill(
            LocalDateTime settleStart,
            LocalDateTime settleEnd,
            boolean onlyMissing,
            int limit) {
        int safeLimit = normalizeLimit(limit);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0);
        if (onlyMissing) {
            wrapper.apply("""
                    NOT EXISTS (
                        SELECT 1 FROM performance_records pr
                        WHERE pr.order_id = colonelsettlement_order.order_id
                    )
                    """);
        }
        if (settleStart != null) {
            wrapper.ge(ColonelsettlementOrder::getSettleTime, settleStart);
        }
        if (settleEnd != null) {
            wrapper.le(ColonelsettlementOrder::getSettleTime, settleEnd);
        }
        wrapper.orderByDesc(ColonelsettlementOrder::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);
        return orderMapper.selectList(wrapper);
    }

    @Override
    public List<ColonelsettlementOrder> findUnsettledOrdersByCreateTimeRange(
            LocalDateTime createStart,
            LocalDateTime createEnd,
            int limit) {
        int safeLimit = normalizeLimit(limit);
        return orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .ge(ColonelsettlementOrder::getCreateTime, createStart)
                .lt(ColonelsettlementOrder::getCreateTime, createEnd)
                .isNull(ColonelsettlementOrder::getSettleTime)
                .orderByDesc(ColonelsettlementOrder::getCreateTime)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public Set<String> findActiveOrderIdsBySettleTimeRange(LocalDateTime settleStart, LocalDateTime settleEnd) {
        if (settleStart == null || settleEnd == null) {
            return Set.of();
        }
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.select("order_id")
                .eq("deleted", 0)
                .ge("settle_time", settleStart)
                .lt("settle_time", settleEnd);
        List<ColonelsettlementOrder> rows = orderMapper.selectList(wrapper);
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

    @Override
    public OrderPage findOrdersCreatedSince(LocalDateTime createStart, long pageNo, long pageSize) {
        if (createStart == null) {
            return new OrderPage(List.of(), 0L);
        }
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_LIMIT));
        Page<ColonelsettlementOrder> page = new Page<>(safePageNo, safePageSize);
        Page<ColonelsettlementOrder> result = orderMapper.selectPage(page, new LambdaQueryWrapper<ColonelsettlementOrder>()
                .ge(ColonelsettlementOrder::getCreateTime, createStart));
        if (result == null || result.getRecords() == null) {
            return new OrderPage(List.of(), 0L);
        }
        return new OrderPage(result.getRecords(), result.getPages());
    }

    @Override
    public OrderPage findOrdersSettledSince(LocalDateTime settleStart, UUID userId, UUID deptId, long pageNo, long pageSize) {
        if (settleStart == null) {
            return new OrderPage(List.of(), 0L);
        }
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_LIMIT));
        Page<ColonelsettlementOrder> page = new Page<>(safePageNo, safePageSize);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .ge(ColonelsettlementOrder::getSettleTime, settleStart);
        if (userId != null) {
            wrapper.eq(ColonelsettlementOrder::getUserId, userId);
        }
        if (deptId != null) {
            wrapper.eq(ColonelsettlementOrder::getDeptId, deptId);
        }
        Page<ColonelsettlementOrder> result = orderMapper.selectPage(page, wrapper);
        if (result == null || result.getRecords() == null) {
            return new OrderPage(List.of(), 0L);
        }
        return new OrderPage(result.getRecords(), result.getPages());
    }

    @Override
    public Set<String> findProductIdsByColonelBuyinId(Long colonelBuyinId) {
        if (colonelBuyinId == null) {
            return Set.of();
        }
        List<ColonelsettlementOrder> orders = orderMapper.selectList(new QueryWrapper<ColonelsettlementOrder>()
                .select("product_id")
                .eq("deleted", 0)
                .isNotNull("product_id")
                .and(wrapper -> wrapper
                        .eq("colonel_buyin_id", colonelBuyinId)
                        .or()
                        .eq("second_colonel_buyin_id", colonelBuyinId)));
        if (orders == null || orders.isEmpty()) {
            return Set.of();
        }
        Set<String> productIds = new LinkedHashSet<>();
        for (ColonelsettlementOrder order : orders) {
            if (order != null && StringUtils.hasText(order.getProductId())) {
                productIds.add(order.getProductId());
            }
        }
        return productIds;
    }

    @Override
    public Map<String, ProductOrderSummary> summarizeProductOrdersByActivity(
            String activityId,
            Collection<String> productIds) {
        if (!StringUtils.hasText(activityId) || productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<String> normalizedProductIds = productIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedProductIds.isEmpty()) {
            return Map.of();
        }
        List<ColonelsettlementOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getActivityId, activityId)
                .in(ColonelsettlementOrder::getProductId, normalizedProductIds)
                .orderByDesc(ColonelsettlementOrder::getCreateTime));
        if (orders == null || orders.isEmpty()) {
            return Map.of();
        }

        Map<String, MutableProductOrderSummary> mutableMap = new LinkedHashMap<>();
        for (ColonelsettlementOrder order : orders) {
            if (order == null || !StringUtils.hasText(order.getProductId())) {
                continue;
            }
            MutableProductOrderSummary summary = mutableMap.computeIfAbsent(
                    order.getProductId(),
                    key -> new MutableProductOrderSummary());
            summary.orderCount++;
            if (STATUS_ATTRIBUTED.equalsIgnoreCase(order.getAttributionStatus())) {
                summary.attributedCount++;
            } else {
                summary.unattributedCount++;
            }
            summary.gmvCent += safeLong(order.getOrderAmount());
            summary.serviceFeeCent += safeLong(order.getSettleColonelCommission());
            LocalDateTime candidateTime = order.getSettleTime() != null ? order.getSettleTime() : order.getCreateTime();
            if (candidateTime != null && (summary.lastOrderTime == null || candidateTime.isAfter(summary.lastOrderTime))) {
                summary.lastOrderTime = candidateTime;
            }
        }
        return mutableMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().freeze(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @Override
    public Map<String, TalentOrderSummary> summarizeTalentOrdersByDouyinUid(
            Collection<String> douyinUids,
            LocalDateTime createStart) {
        if (douyinUids == null || douyinUids.isEmpty()) {
            return Map.of();
        }
        List<String> normalized = douyinUids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return Map.of();
        }
        Map<String, TalentOrderSummary> result = new LinkedHashMap<>();
        for (int offset = 0; offset < normalized.size(); offset += DEFAULT_LIMIT) {
            List<String> batch = normalized.subList(offset, Math.min(offset + DEFAULT_LIMIT, normalized.size()));
            QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<ColonelsettlementOrder>()
                    .select("""
                            COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name) AS talent_uid
                            """,
                            "COUNT(1) AS order_count",
                            "COALESCE(SUM(order_amount), 0) AS order_amount",
                            "COALESCE(SUM(settle_colonel_commission), 0) AS service_fee")
                    .eq("deleted", 0)
                    .in("COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name)", batch)
                    .groupBy("COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name)");
            if (createStart != null) {
                wrapper.ge("create_time", createStart);
            }
            for (Map<String, Object> row : orderMapper.selectMaps(wrapper)) {
                String talentUid = asString(readMapValue(row, "talent_uid"));
                if (StringUtils.hasText(talentUid)) {
                    result.put(talentUid, new TalentOrderSummary(
                            talentUid,
                            asLong(readMapValue(row, "order_count")),
                            asLong(readMapValue(row, "order_amount")),
                            asLong(readMapValue(row, "service_fee"))));
                }
            }
        }
        return result;
    }

    @Override
    public List<TalentRecentOrder> findRecentOrdersByTalentUid(String douyinUid, int limit) {
        if (!StringUtils.hasText(douyinUid)) {
            return List.of();
        }
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, DEFAULT_LIMIT);
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("order_id",
                        "COALESCE(product_title, product_name) AS product_name",
                        "order_amount",
                        "settle_colonel_commission AS service_fee",
                        "channel_user_name",
                        "create_time")
                .eq("deleted", 0)
                .apply("COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name) = {0}", douyinUid.trim())
                .orderByDesc("create_time")
                .last("LIMIT " + safeLimit);
        return orderMapper.selectMaps(wrapper).stream()
                .map(row -> new TalentRecentOrder(
                        asString(readMapValue(row, "order_id")),
                        asString(readMapValue(row, "product_name")),
                        asLong(readMapValue(row, "order_amount")),
                        asLong(readMapValue(row, "service_fee")),
                        asString(readMapValue(row, "channel_user_name")),
                        asDateTime(readMapValue(row, "create_time"))))
                .toList();
    }

    @Override
    public DashboardAttributionSummary getDashboardAttributionSummary(
            LocalDateTime settleStart,
            LocalDateTime settleEnd,
            OrderVisibility visibility) {
        QueryWrapper<ColonelsettlementOrder> attributedWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .eq("attribution_status", STATUS_ATTRIBUTED);
        applyDashboardRange(attributedWrapper, settleStart, settleEnd);
        applyDashboardVisibility(attributedWrapper, visibility);
        long attributedCount = safeLong(orderMapper.selectCount(attributedWrapper));

        QueryWrapper<ColonelsettlementOrder> unattributedWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .eq("attribution_status", STATUS_UNATTRIBUTED);
        applyDashboardRange(unattributedWrapper, settleStart, settleEnd);
        applyDashboardVisibility(unattributedWrapper, visibility);
        long unattributedCount = safeLong(orderMapper.selectCount(unattributedWrapper));

        QueryWrapper<ColonelsettlementOrder> reasonWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("attribution_remark as reason", "count(*) as count")
                .eq("attribution_status", STATUS_UNATTRIBUTED)
                .isNotNull("attribution_remark")
                .groupBy("attribution_remark");
        applyDashboardRange(reasonWrapper, settleStart, settleEnd);
        applyDashboardVisibility(reasonWrapper, visibility);
        List<DashboardReasonCount> reasons = orderMapper.selectMaps(reasonWrapper).stream()
                .map(row -> new DashboardReasonCount(
                        asString(readMapValue(row, "reason")),
                        asLong(readMapValue(row, "count"))))
                .sorted(Comparator.comparingLong(DashboardReasonCount::count).reversed())
                .toList();

        return new DashboardAttributionSummary(attributedCount, unattributedCount, reasons);
    }

    @Override
    public DashboardFallbackSummary getDashboardFallbackSummary(
            LocalDateTime settleStart,
            LocalDateTime settleEnd,
            OrderVisibility visibility) {
        QueryWrapper<ColonelsettlementOrder> totalWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("count(*) as orderCount",
                        "sum(order_amount) as orderAmount",
                        "sum(settle_colonel_commission) as serviceFee");
        applyDashboardRange(totalWrapper, settleStart, settleEnd);
        applyDashboardVisibility(totalWrapper, visibility);
        Map<String, Object> totalMap = orderMapper.selectMaps(totalWrapper).stream()
                .findFirst()
                .orElse(Map.of());

        QueryWrapper<ColonelsettlementOrder> channelWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("channel_user_id as channelUserId",
                        "channel_user_name as channelUserName",
                        "count(*) as orderCount",
                        "sum(order_amount) as orderAmount",
                        "sum(settle_colonel_commission) as serviceFee")
                .isNotNull("channel_user_id")
                .eq("attribution_status", STATUS_ATTRIBUTED)
                .groupBy("channel_user_id", "channel_user_name");
        applyDashboardRange(channelWrapper, settleStart, settleEnd);
        applyDashboardVisibility(channelWrapper, visibility);
        List<DashboardPerformanceItem> channelPerformance = orderMapper.selectMaps(channelWrapper).stream()
                .map(row -> toDashboardPerformanceItem(row, "channelUserId", "channelUserName"))
                .sorted(Comparator.comparingLong(DashboardPerformanceItem::orderCount).reversed())
                .limit(10)
                .toList();

        QueryWrapper<ColonelsettlementOrder> colonelWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("colonel_user_id as colonelUserId",
                        "colonel_user_name as colonelUserName",
                        "count(*) as orderCount",
                        "sum(order_amount) as orderAmount",
                        "sum(settle_colonel_commission) as serviceFee")
                .isNotNull("colonel_user_id")
                .eq("attribution_status", STATUS_ATTRIBUTED)
                .groupBy("colonel_user_id", "colonel_user_name");
        applyDashboardRange(colonelWrapper, settleStart, settleEnd);
        applyDashboardVisibility(colonelWrapper, visibility);
        List<DashboardPerformanceItem> colonelPerformance = orderMapper.selectMaps(colonelWrapper).stream()
                .map(row -> toDashboardPerformanceItem(row, "colonelUserId", "colonelUserName"))
                .sorted(Comparator.comparingLong(DashboardPerformanceItem::orderCount).reversed())
                .limit(10)
                .toList();

        return new DashboardFallbackSummary(
                asLong(readMapValue(totalMap, "orderCount")),
                asLong(readMapValue(totalMap, "orderAmount")),
                asLong(readMapValue(totalMap, "serviceFee")),
                channelPerformance,
                colonelPerformance);
    }

    private static DashboardPerformanceItem toDashboardPerformanceItem(
            Map<String, Object> row,
            String userIdKey,
            String userNameKey) {
        return new DashboardPerformanceItem(
                asString(readMapValue(row, userIdKey)),
                asString(readMapValue(row, userNameKey)),
                asLong(readMapValue(row, "orderCount")),
                asLong(readMapValue(row, "orderAmount")),
                asLong(readMapValue(row, "serviceFee")));
    }

    private static void applyDashboardRange(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            LocalDateTime settleStart,
            LocalDateTime settleEnd) {
        if (settleStart != null) {
            wrapper.ge("settle_time", settleStart);
        }
        if (settleEnd != null) {
            wrapper.le("settle_time", settleEnd);
        }
    }

    private static void applyDashboardVisibility(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            OrderVisibility visibility) {
        OrderVisibility safeVisibility = visibility == null ? OrderVisibility.all() : visibility;
        switch (safeVisibility.type()) {
            case ALL -> {
                // no filter
            }
            case USER -> {
                if (safeVisibility.userId() == null) {
                    wrapper.apply("1 = 0");
                    return;
                }
                wrapper.eq("user_id", safeVisibility.userId());
            }
            case DEPT -> {
                if (safeVisibility.deptId() == null) {
                    wrapper.apply("1 = 0");
                    return;
                }
                wrapper.eq("dept_id", safeVisibility.deptId());
            }
            case NONE -> wrapper.apply("1 = 0");
        }
    }

    private static Object readMapValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        if (map.containsKey(key)) {
            return map.get(key);
        }
        return map.get(key.toLowerCase());
    }

    private static long asLong(Object val) {
        if (val instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static long safeLong(Long val) {
        return val == null ? 0L : val;
    }

    private static String asString(Object val) {
        return val == null ? null : String.valueOf(val);
    }

    private static LocalDateTime asDateTime(Object val) {
        if (val instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (val instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private static final class MutableProductOrderSummary {
        private long orderCount;
        private long attributedCount;
        private long unattributedCount;
        private long gmvCent;
        private long serviceFeeCent;
        private LocalDateTime lastOrderTime;

        private ProductOrderSummary freeze() {
            return new ProductOrderSummary(
                    orderCount,
                    attributedCount,
                    unattributedCount,
                    gmvCent,
                    serviceFeeCent,
                    lastOrderTime);
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
