package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.controller.OrderController;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;

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
    IPage<ColonelsettlementOrder> getOrders(
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
     * 获取订单详情。
     */
    OrderDetailResponse getOrderDetail(String orderId, UUID userId, UUID deptId, DataScope dataScope);

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
}
