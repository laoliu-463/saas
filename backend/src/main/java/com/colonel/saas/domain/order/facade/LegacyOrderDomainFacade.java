package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.controller.OrderController;
import com.colonel.saas.domain.order.query.OrderDetailView;
import com.colonel.saas.domain.order.query.OrderQueryView;
import com.colonel.saas.domain.order.query.OrderListAssembler;
import com.colonel.saas.domain.order.query.OrderDetailAssembler;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.OrderService;
import com.colonel.saas.service.OrderSyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 遗留委派只读门面实现类（DDD-ORDER-003）。
 *
 * <p>将 Controller 层请求委派给 legacy {@link OrderService} / {@link OrderQueryService}，
 * 并通过 Assembler 转换为 domain view。数据范围过滤由 OrderService 内部处理。</p>
 */
@Service
public class LegacyOrderDomainFacade implements OrderDomainFacade {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private final OrderSyncService orderSyncService;

    public LegacyOrderDomainFacade(
            OrderService orderService,
            OrderQueryService orderQueryService,
            OrderSyncService orderSyncService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
        this.orderSyncService = orderSyncService;
    }

    @Transactional(readOnly = true, timeout = 15)
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
        return orderService.findPage(
                page, size, orderId, attributionStatus, unattributedReason,
                activityId, productId, channelKeyword, colonelKeyword,
                orderStatus, startTime, endTime, timeField, dashboardDiagnosis,
                parseUuidCsv(recruiterDeptIds), parseUuidCsv(channelDeptIds),
                userId, deptId, dataScope
        ).convert(OrderListAssembler::toView);
    }

    @Transactional(readOnly = true, timeout = 15)
    @Override
    public OrderDetailView getOrderDetail(String orderId, UUID userId, UUID deptId, DataScope dataScope) {
        OrderDetailResponse response = orderQueryService.getOrderDetail(orderId, userId, deptId, dataScope);
        return OrderDetailAssembler.toView(response);
    }

    @Transactional(readOnly = true, timeout = 15)
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
        OrderService.OrderStatsResult result = orderService.findStats(
                orderId, attributionStatus, unattributedReason,
                activityId, productId, channelKeyword, colonelKeyword,
                orderStatus, startTime, endTime, timeField, dashboardDiagnosis,
                parseUuidCsv(recruiterDeptIds), parseUuidCsv(channelDeptIds),
                userId, deptId, dataScope,
                orderSyncService.getLastSyncTime()
        );
        return toOrderStats(result);
    }

    private OrderController.OrderStats toOrderStats(OrderService.OrderStatsResult result) {
        OrderController.OrderStats stats = new OrderController.OrderStats();
        if (result == null) {
            return stats;
        }
        stats.setTotalOrders(result.totalOrders());
        stats.setAttributedOrders(result.attributedOrders());
        stats.setUnattributedOrders(result.unattributedOrders());
        stats.setPartialOrders(result.partialOrders());
        stats.setSyncFailedOrders(result.syncFailedOrders());
        stats.setLastSyncTime(result.lastSyncTime());
        stats.setUnattributedReasons(result.unattributedReasons() == null
                ? List.of()
                : result.unattributedReasons().stream()
                .map(reason -> new OrderController.ReasonCount(reason.reason(), reason.count()))
                .toList());
        return stats;
    }

    static List<UUID> parseUuidCsv(String csv) {
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
}
