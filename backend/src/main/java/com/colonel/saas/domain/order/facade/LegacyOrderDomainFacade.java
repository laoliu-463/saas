package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.controller.OrderController;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.domain.order.query.OrderDetailView;
import com.colonel.saas.domain.order.query.OrderQueryView;
import com.colonel.saas.domain.order.query.OrderListAssembler;
import com.colonel.saas.domain.order.query.OrderDetailAssembler;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.DashboardService;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.OrderService;
import com.colonel.saas.service.OrderSyncService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 遗留委派只读门面实现类（DDD-ORDER-003）。
 */
@Service
public class LegacyOrderDomainFacade implements OrderDomainFacade {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private final ColonelsettlementOrderMapper orderMapper;
    private final OrderSyncService orderSyncService;

    public LegacyOrderDomainFacade(
            OrderService orderService,
            OrderQueryService orderQueryService,
            ColonelsettlementOrderMapper orderMapper,
            OrderSyncService orderSyncService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
        this.orderMapper = orderMapper;
        this.orderSyncService = orderSyncService;
    }

    @Override
    public IPage<OrderQueryView> getOrders(
            long page,
            long size,
            String orderId,
            String attributionStatus,
            String unattributedReason,
            String activityId,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime,
            String timeField,
            String dashboardDiagnosis,
            String recruiterDeptIds,
            String channelDeptIds,
            UUID userId,
            UUID deptId,
            DataScope dataScope
    ) {
        Page<ColonelsettlementOrder> query = new Page<>(page, size);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = buildWrapper(
                orderId,
                attributionStatus,
                unattributedReason,
                activityId,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime,
                timeField,
                dashboardDiagnosis,
                parseUuidCsv(recruiterDeptIds),
                parseUuidCsv(channelDeptIds)
        );
        selectOrderListColumns(wrapper);
        applyDataScope(wrapper, userId, deptId, dataScope);
        wrapper.orderByDesc(ColonelsettlementOrder::getUpdateTime)
                .orderByDesc(ColonelsettlementOrder::getCreateTime);
        IPage<ColonelsettlementOrder> result = orderMapper.selectPage(query, wrapper);
        result.getRecords().forEach(orderService::normalizeOrderRow);
        orderService.enrichOrderList(result.getRecords());
        return result.convert(OrderListAssembler::toView);
    }

    @Override
    public OrderDetailView getOrderDetail(String orderId, UUID userId, UUID deptId, DataScope dataScope) {
        OrderDetailResponse response = orderQueryService.getOrderDetail(orderId, userId, deptId, dataScope);
        return OrderDetailAssembler.toView(response);
    }


    @Override
    public OrderController.OrderStats getStats(
            String orderId,
            String attributionStatus,
            String unattributedReason,
            String activityId,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime,
            String timeField,
            String dashboardDiagnosis,
            String recruiterDeptIds,
            String channelDeptIds,
            UUID userId,
            UUID deptId,
            DataScope dataScope
    ) {
        List<UUID> parsedRecruiterDeptIds = parseUuidCsv(recruiterDeptIds);
        List<UUID> parsedChannelDeptIds = parseUuidCsv(channelDeptIds);
        QueryWrapper<ColonelsettlementOrder> statusWrapper = buildStatsWrapper(
                orderId,
                attributionStatus,
                unattributedReason,
                activityId,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime,
                timeField,
                dashboardDiagnosis,
                parsedRecruiterDeptIds,
                parsedChannelDeptIds
        );
        applyQueryDataScope(statusWrapper, userId, deptId, dataScope);
        OrderController.OrderStats stats = new OrderController.OrderStats();
        stats.setLastSyncTime(orderSyncService.getLastSyncTime());
        statusWrapper.select("attribution_status AS attributionStatus", "COUNT(*) AS total")
                .groupBy("attribution_status");

        long totalOrders = 0L;
        long attributedOrders = 0L;
        long unattributedOrders = 0L;
        long partialOrders = 0L;
        for (Map<String, Object> row : orderMapper.selectMaps(statusWrapper)) {
            String status = asText(readValue(row, "attributionStatus"));
            long count = asLong(readValue(row, "total"));
            totalOrders += count;
            if (AttributionService.STATUS_ATTRIBUTED.equals(status)) {
                attributedOrders += count;
            }
            if (AttributionService.STATUS_UNATTRIBUTED.equals(status)) {
                unattributedOrders += count;
            }
            if ("PARTIAL".equals(status)) {
                partialOrders += count;
            }
        }

        QueryWrapper<ColonelsettlementOrder> reasonWrapper = buildStatsWrapper(
                orderId,
                attributionStatus,
                unattributedReason,
                activityId,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime,
                timeField,
                dashboardDiagnosis,
                parsedRecruiterDeptIds,
                parsedChannelDeptIds
        );
        applyQueryDataScope(reasonWrapper, userId, deptId, dataScope);
        reasonWrapper.eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                .isNotNull("attribution_remark")
                .select("attribution_remark AS reason", "COUNT(*) AS total")
                .groupBy("attribution_remark");

        List<OrderController.ReasonCount> reasonCounts = new ArrayList<>();
        long syncFailedOrders = 0L;
        for (Map<String, Object> row : orderMapper.selectMaps(reasonWrapper)) {
            String reason = asText(readValue(row, "reason"));
            long count = asLong(readValue(row, "total"));
            if (!StringUtils.hasText(reason)) {
                continue;
            }
            reasonCounts.add(new OrderController.ReasonCount(reason, count));
            if (AttributionService.REASON_SYNC_FAILED.equals(reason)) {
                syncFailedOrders += count;
            }
        }

        stats.setTotalOrders(totalOrders);
        stats.setAttributedOrders(attributedOrders);
        stats.setUnattributedOrders(unattributedOrders);
        stats.setPartialOrders(partialOrders);
        stats.setSyncFailedOrders(syncFailedOrders);
        stats.setUnattributedReasons(reasonCounts.stream()
                .sorted(Comparator.comparingLong(OrderController.ReasonCount::count).reversed())
                .toList());
        return stats;
    }

    private LambdaQueryWrapper<ColonelsettlementOrder> buildWrapper(
            String orderId,
            String attributionStatus,
            String unattributedReason,
            String activityId,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime,
            String timeField,
            String dashboardDiagnosis,
            List<UUID> recruiterDeptIds,
            List<UUID> channelDeptIds) {
        LocalDateTime start = parseLocalDateTime(startTime);
        LocalDateTime end = parseLocalDateTime(endTime);
        List<UUID> normalizedRecruiterDeptIds = normalizeUuidList(recruiterDeptIds);
        List<UUID> normalizedChannelDeptIds = normalizeUuidList(channelDeptIds);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .eq(StringUtils.hasText(orderId), ColonelsettlementOrder::getOrderId, orderId)
                .eq(StringUtils.hasText(unattributedReason), ColonelsettlementOrder::getAttributionRemark, unattributedReason)
                .eq(StringUtils.hasText(activityId), ColonelsettlementOrder::getActivityId, activityId)
                .eq(StringUtils.hasText(productId), ColonelsettlementOrder::getProductId, productId)
                .eq(orderStatus != null, ColonelsettlementOrder::getOrderStatus, orderStatus)
                .in(!normalizedRecruiterDeptIds.isEmpty(), ColonelsettlementOrder::getDeptId, normalizedRecruiterDeptIds)
                .in(!normalizedChannelDeptIds.isEmpty(), ColonelsettlementOrder::getChannelDeptId, normalizedChannelDeptIds)
                .and(StringUtils.hasText(channelKeyword), nested -> nested
                        .like(ColonelsettlementOrder::getChannelUserName, channelKeyword)
                        .or()
                        .like(ColonelsettlementOrder::getChannelUserId, channelKeyword))
                .and(StringUtils.hasText(colonelKeyword), nested -> nested
                        .like(ColonelsettlementOrder::getColonelUserName, colonelKeyword)
                        .or()
                        .like(ColonelsettlementOrder::getColonelUserId, colonelKeyword));
        applyAttributionStatusFilter(wrapper, attributionStatus);
        applyTimeRange(wrapper, resolveTimeField(timeField), start, end);
        applyDashboardDiagnosisFilter(wrapper, null, dashboardDiagnosis);
        return wrapper;
    }

    private QueryWrapper<ColonelsettlementOrder> buildStatsWrapper(
            String orderId,
            String attributionStatus,
            String unattributedReason,
            String activityId,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime,
            String timeField,
            String dashboardDiagnosis,
            List<UUID> recruiterDeptIds,
            List<UUID> channelDeptIds) {
        LocalDateTime start = parseLocalDateTime(startTime);
        LocalDateTime end = parseLocalDateTime(endTime);
        List<UUID> normalizedRecruiterDeptIds = normalizeUuidList(recruiterDeptIds);
        List<UUID> normalizedChannelDeptIds = normalizeUuidList(channelDeptIds);
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq(StringUtils.hasText(orderId), "order_id", orderId)
                .eq(StringUtils.hasText(unattributedReason), "attribution_remark", unattributedReason)
                .eq(StringUtils.hasText(activityId), "colonel_activity_id", activityId)
                .eq(StringUtils.hasText(productId), "product_id", productId)
                .eq(orderStatus != null, "order_status", orderStatus)
                .in(!normalizedRecruiterDeptIds.isEmpty(), "dept_id", normalizedRecruiterDeptIds)
                .in(!normalizedChannelDeptIds.isEmpty(), "channel_dept_id", normalizedChannelDeptIds)
                .and(StringUtils.hasText(channelKeyword), nested -> nested
                        .like("channel_user_name", channelKeyword)
                        .or()
                        .like("channel_user_id", channelKeyword))
                .and(StringUtils.hasText(colonelKeyword), nested -> nested
                        .like("colonel_user_name", colonelKeyword)
                        .or()
                        .like("colonel_user_id", colonelKeyword));
        applyAttributionStatusFilter(wrapper, attributionStatus);
        applyTimeRange(wrapper, resolveTimeField(timeField), start, end);
        applyDashboardDiagnosisFilter(wrapper, null, dashboardDiagnosis);
        return wrapper;
    }

    private void applyAttributionStatusFilter(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            String attributionStatus) {
        if (wrapper == null || !StringUtils.hasText(attributionStatus)) {
            return;
        }
        if (AttributionService.STATUS_UNATTRIBUTED.equals(attributionStatus)) {
            wrapper.and(nested -> nested
                    .eq(ColonelsettlementOrder::getAttributionStatus, AttributionService.STATUS_UNATTRIBUTED)
                    .or()
                    .isNull(ColonelsettlementOrder::getAttributionStatus));
            return;
        }
        wrapper.eq(ColonelsettlementOrder::getAttributionStatus, attributionStatus);
    }

    private void applyAttributionStatusFilter(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            String attributionStatus) {
        if (wrapper == null || !StringUtils.hasText(attributionStatus)) {
            return;
        }
        if (AttributionService.STATUS_UNATTRIBUTED.equals(attributionStatus)) {
            wrapper.and(nested -> nested
                    .eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                    .or()
                    .isNull("attribution_status"));
            return;
        }
        wrapper.eq("attribution_status", attributionStatus);
    }

    private void applyTimeRange(LambdaQueryWrapper<ColonelsettlementOrder> wrapper, String timeField, LocalDateTime start, LocalDateTime end) {
        if ("settle_time".equals(timeField)) {
            wrapper.ge(start != null, ColonelsettlementOrder::getSettleTime, start)
                    .le(end != null, ColonelsettlementOrder::getSettleTime, end);
            return;
        }
        wrapper.ge(start != null, ColonelsettlementOrder::getCreateTime, start)
                .le(end != null, ColonelsettlementOrder::getCreateTime, end);
    }

    private void applyTimeRange(QueryWrapper<ColonelsettlementOrder> wrapper, String timeField, LocalDateTime start, LocalDateTime end) {
        wrapper.ge(start != null, timeField, start)
                .le(end != null, timeField, end);
    }

    private void selectOrderListColumns(LambdaQueryWrapper<ColonelsettlementOrder> wrapper) {
        if (wrapper == null) {
            return;
        }
        wrapper.select(ColonelsettlementOrder.class, field -> !"extra_data".equals(field.getColumn()));
    }

    private String resolveTimeField(String timeField) {
        return "settleTime".equalsIgnoreCase(timeField) ? "settle_time" : "create_time";
    }

    private void applyDashboardDiagnosisFilter(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            String alias,
            String dashboardDiagnosis) {
        if (wrapper == null || !StringUtils.hasText(dashboardDiagnosis)) {
            return;
        }
        String prefix = StringUtils.hasText(alias) ? alias + "." : "colonelsettlement_order.";
        applyDiagnosisSql(wrapper, prefix, dashboardDiagnosis.trim());
    }

    private void applyDashboardDiagnosisFilter(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            String alias,
            String dashboardDiagnosis) {
        if (wrapper == null || !StringUtils.hasText(dashboardDiagnosis)) {
            return;
        }
        String prefix = StringUtils.hasText(alias) ? alias + "." : "colonelsettlement_order.";
        applyDiagnosisSql(wrapper, prefix, dashboardDiagnosis.trim());
    }

    private void applyDiagnosisSql(LambdaQueryWrapper<ColonelsettlementOrder> wrapper, String prefix, String diagnosis) {
        String normalizedDiagnosis = DashboardService.normalizeDiagnosisCategory(diagnosis);
        if (!StringUtils.hasText(normalizedDiagnosis)) {
            return;
        }
        String safePrefix = sanitizeDiagnosisSqlPrefix(prefix);
        String categorySql = DashboardService.diagnosisCategoryCaseSql(
                safePrefix + "colonel_activity_id",
                safePrefix + "second_colonel_activity_id",
                safePrefix + "product_id",
                safePrefix + "create_time",
                safePrefix + "colonel_buyin_id",
                safePrefix + "attribution_status",
                safePrefix + "attribution_remark"
        );
        wrapper.apply("(" + categorySql + ") = {0}", normalizedDiagnosis);
    }

    private void applyDiagnosisSql(QueryWrapper<ColonelsettlementOrder> wrapper, String prefix, String diagnosis) {
        String normalizedDiagnosis = DashboardService.normalizeDiagnosisCategory(diagnosis);
        if (!StringUtils.hasText(normalizedDiagnosis)) {
            return;
        }
        String safePrefix = sanitizeDiagnosisSqlPrefix(prefix);
        String categorySql = DashboardService.diagnosisCategoryCaseSql(
                safePrefix + "colonel_activity_id",
                safePrefix + "second_colonel_activity_id",
                safePrefix + "product_id",
                safePrefix + "create_time",
                safePrefix + "colonel_buyin_id",
                safePrefix + "attribution_status",
                safePrefix + "attribution_remark"
        );
        wrapper.apply("(" + categorySql + ") = {0}", normalizedDiagnosis);
    }

    private String sanitizeDiagnosisSqlPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "colonelsettlement_order.";
        }
        String normalized = prefix.trim();
        if ("colonelsettlement_order.".equals(normalized) || "fo.".equals(normalized)) {
            return normalized;
        }
        return "colonelsettlement_order.";
    }

    private void applyDataScope(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null) {
                    wrapper.eq(ColonelsettlementOrder::getUserId, userId);
                }
            }
            case DEPT -> {
                if (deptId != null) {
                    wrapper.eq(ColonelsettlementOrder::getDeptId, deptId);
                }
            }
            case ALL -> {
                // no filter
            }
        }
    }

    private void applyQueryDataScope(
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null) {
                    wrapper.eq("user_id", userId);
                }
            }
            case DEPT -> {
                if (deptId != null) {
                    wrapper.eq("dept_id", deptId);
                }
            }
            case ALL -> {
                // no filter
            }
        }
    }

    private static List<UUID> normalizeUuidList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static List<UUID> parseUuidCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private Object readValue(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty() || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private LocalDateTime parseLocalDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }
}
