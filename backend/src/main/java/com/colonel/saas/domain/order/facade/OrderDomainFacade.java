package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.controller.OrderController;
import com.colonel.saas.domain.order.query.OrderDetailView;
import com.colonel.saas.domain.order.query.OrderQueryView;

import java.util.UUID;

/**
 * 订单域只读门面接口（DDD-ORDER-003）。
 * <p>
 * 用于隔离控制器直接操作订单数据库和 MyBatis-Plus Mapper，统一通过 Facade 进行查询和统计。
 * </p>
 */
public interface OrderDomainFacade {

    /**
     * 分页查询订单列表。
     */
    IPage<OrderQueryView> getOrders(
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
    );

    /**
     * 角色化订单查询。默认实现保留旧门面兼容，具体实现应按订单归因事实过滤。
     */
    default IPage<OrderQueryView> getOrders(
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
            DataScope dataScope,
            Object roleCodes
    ) {
        return getOrders(
                page, size, orderId, attributionStatus, unattributedReason, activityId, productId,
                channelKeyword, colonelKeyword, orderStatus, startTime, endTime, timeField,
                dashboardDiagnosis, recruiterDeptIds, channelDeptIds, userId, deptId, dataScope);
    }

    /**
     * 获取订单详情。
     */
    OrderDetailView getOrderDetail(String orderId, UUID userId, UUID deptId, DataScope dataScope);

    /**
     * 角色化订单详情查询。默认实现保留旧门面兼容。
     */
    default OrderDetailView getOrderDetail(
            String orderId,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes) {
        return getOrderDetail(orderId, userId, deptId, dataScope);
    }

    /**
     * 获取订单汇总统计。
     */
    OrderController.OrderStats getStats(
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
    );

    /**
     * 角色化订单统计。默认实现保留旧门面兼容，具体实现应与列表使用同一范围。
     */
    default OrderController.OrderStats getStats(
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
            DataScope dataScope,
            Object roleCodes
    ) {
        return getStats(
                orderId, attributionStatus, unattributedReason, activityId, productId, channelKeyword,
                colonelKeyword, orderStatus, startTime, endTime, timeField, dashboardDiagnosis,
                recruiterDeptIds, channelDeptIds, userId, deptId, dataScope);
    }
}
