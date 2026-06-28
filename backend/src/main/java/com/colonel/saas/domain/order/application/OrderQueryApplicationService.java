package com.colonel.saas.domain.order.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.domain.order.query.OrderQueryService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 订单查询应用层入口（DDD-ORDER-006 查询 / 同步模型解耦）。
 *
 * <p>读模型独立入口，与 {@link OrderSyncApplicationService}（写模型入口）并列：
 * <ul>
 *   <li>{@link OrderSyncApplicationService} — 负责订单事实同步、归因、写入</li>
 *   <li>本服务 — 负责订单详情查询、读模型装配、权限校验</li>
 * </ul>
 * </p>
 *
 * <p>当前为薄壳：所有真实读取逻辑仍在 {@code service.OrderQueryService}，
 * 本类作为"读侧"在 domain 层的官方入口，便于未来把 SQL 装配 / 权限校验 / 数据
 * 范围消费迁移到 {@code domain.order.query} 子包下的 QueryPolicy。</p>
 *
 * <p>不引入新的 DDD 开关（与同步侧 {@code order-application} 开关解耦）——
 * 直接作为查询 API 的稳定入口，由 Controller / Facade 调用。</p>
 */
@Service
public class OrderQueryApplicationService {

    private final OrderQueryService orderQueryService;

    public OrderQueryApplicationService(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    /**
     * 订单详情查询。
     *
     * <p>委派 {@link OrderQueryService#getOrderDetail}，参数与返回值完全一致，
     * 调用方可以无感切换到本入口。</p>
     *
     * @param orderId       订单业务 ID
     * @param currentUserId 当前操作用户 ID
     * @param currentDeptId 当前操作用户所属部门 ID
     * @param dataScope     当前用户的可见数据范围
     * @return 订单详情 DTO
     */
    public OrderDetailResponse getOrderDetail(
            String orderId,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope) {
        return orderQueryService.getOrderDetail(orderId, currentUserId, currentDeptId, dataScope);
    }
}
