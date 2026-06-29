package com.colonel.saas.domain.order.facade;

import com.colonel.saas.entity.ColonelsettlementOrder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 订单域内部读门面（DDD-CLEAN-003）。
 * <p>
 * 业绩域、事件监听器等跨域模块应通过本接口读取订单事实，
 * 禁止直接注入 {@code ColonelsettlementOrderMapper}。
 * </p>
 */
public interface OrderReadFacade {

    /** 按业务 orderId 查询有效订单，不存在时返回 null。 */
    ColonelsettlementOrder findByOrderId(String orderId);

    /** 有效订单是否存在（deleted = 0）。 */
    boolean existsActiveByOrderId(String orderId);

    /** 按 orderId 列表批量查询有效订单。 */
    List<ColonelsettlementOrder> findByOrderIds(Collection<String> orderIds);

    /** 失效/退款订单上仍存在有效业绩记录的订单（业绩冲正回填）。 */
    List<ColonelsettlementOrder> findInvalidatedOrdersWithStalePerformance(int limit);

    /**
     * 业绩回填候选订单：按 settleTime 范围筛选，可选仅缺失业绩记录。
     */
    List<ColonelsettlementOrder> findOrdersForBackfill(
            LocalDateTime settleStart,
            LocalDateTime settleEnd,
            boolean onlyMissing,
            int limit);

    /** 指定 createTime 范围内未结算（settleTime 为空）的订单。 */
    List<ColonelsettlementOrder> findUnsettledOrdersByCreateTimeRange(
            LocalDateTime createStart,
            LocalDateTime createEnd,
            int limit);

    /** 按 settleTime 半开区间读取有效订单号，用于只读对账。 */
    Set<String> findActiveOrderIdsBySettleTimeRange(LocalDateTime settleStart, LocalDateTime settleEnd);

    /** 按 createTime 起点分页读取订单事实。 */
    OrderPage findOrdersCreatedSince(LocalDateTime createStart, long pageNo, long pageSize);

    /** 按 settleTime 起点分页读取订单事实，可选按用户或部门过滤。 */
    OrderPage findOrdersSettledSince(LocalDateTime settleStart, UUID userId, UUID deptId, long pageNo, long pageSize);

    record OrderPage(List<ColonelsettlementOrder> records, long pages) {
    }
}
