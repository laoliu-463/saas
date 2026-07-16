package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.order.application.OrderDetailQueryApplicationService;
import com.colonel.saas.dto.order.OrderDetailResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 订单查询服务（DDD 委派壳，DDD-ORDER-006 Slice 1）。
 *
 * <p>本类为 1-line 委派壳，所有真实业务逻辑已搬至
 * {@link OrderDetailQueryApplicationService}。
 * 现有调用方（Controller / Facade）继续通过本类调用，行为零变化。</p>
 */
@Service
public class OrderQueryService {

    private final OrderDetailQueryApplicationService applicationService;

    public OrderQueryService(@Lazy OrderDetailQueryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public OrderDetailResponse getOrderDetail(
            String orderId,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope) {
        return applicationService.getOrderDetail(orderId, currentUserId, currentDeptId, dataScope);
    }

    /**
     * 传递认证请求的角色事实，详情与列表/统计使用同一归因维度范围。
     */
    public OrderDetailResponse getOrderDetail(
            String orderId,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            Object roleCodes) {
        return applicationService.getOrderDetail(orderId, currentUserId, currentDeptId, dataScope, roleCodes);
    }
}
