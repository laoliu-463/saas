package com.colonel.saas.domain.order.facade;

import com.colonel.saas.entity.ColonelsettlementOrder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /** 商品域团长筛选：按团长 buyin id 读取关联商品 ID。 */
    Set<String> findProductIdsByColonelBuyinId(Long colonelBuyinId);

    /** 商品域活动商品视图：按活动和商品集合读取订单事实摘要。 */
    Map<String, ProductOrderSummary> summarizeProductOrdersByActivity(
            String activityId,
            Collection<String> productIds);

    /** 达人域列表卡片：按达人 UID 集合聚合订单事实摘要。 */
    Map<String, TalentOrderSummary> summarizeTalentOrdersByDouyinUid(
            Collection<String> douyinUids,
            LocalDateTime createStart);

    /** 达人域详情页：按达人 UID 读取最近订单事实。 */
    List<TalentRecentOrder> findRecentOrdersByTalentUid(String douyinUid, int limit);

    /** Dashboard 订单归因计数与未归因原因，只读订单事实。 */
    DashboardAttributionSummary getDashboardAttributionSummary(
            LocalDateTime settleStart,
            LocalDateTime settleEnd,
            OrderVisibility visibility);

    /** Dashboard 在没有业绩汇总表时使用的订单事实回退聚合。 */
    DashboardFallbackSummary getDashboardFallbackSummary(
            LocalDateTime settleStart,
            LocalDateTime settleEnd,
            OrderVisibility visibility);

    record OrderPage(List<ColonelsettlementOrder> records, long pages) {
    }

    enum OrderVisibilityType {
        ALL, USER, DEPT, CHANNEL_USER, CHANNEL_DEPT, NONE
    }

    record OrderVisibility(OrderVisibilityType type, UUID userId, UUID deptId) {
        public static OrderVisibility all() {
            return new OrderVisibility(OrderVisibilityType.ALL, null, null);
        }

        public static OrderVisibility user(UUID userId) {
            return new OrderVisibility(OrderVisibilityType.USER, userId, null);
        }

        public static OrderVisibility dept(UUID deptId) {
            return new OrderVisibility(OrderVisibilityType.DEPT, null, deptId);
        }

        public static OrderVisibility channelUser(UUID userId) {
            return new OrderVisibility(OrderVisibilityType.CHANNEL_USER, userId, null);
        }

        public static OrderVisibility channelDept(UUID deptId) {
            return new OrderVisibility(OrderVisibilityType.CHANNEL_DEPT, null, deptId);
        }

        public static OrderVisibility none() {
            return new OrderVisibility(OrderVisibilityType.NONE, null, null);
        }
    }

    record DashboardAttributionSummary(
            long attributedOrderCount,
            long unattributedOrderCount,
            List<DashboardReasonCount> unattributedReasons) {
    }

    record DashboardFallbackSummary(
            long orderCount,
            long orderAmountCent,
            long serviceFeeCent,
            List<DashboardPerformanceItem> channelPerformance,
            List<DashboardPerformanceItem> colonelPerformance) {
    }

    record DashboardPerformanceItem(
            String userId,
            String userName,
            long orderCount,
            long orderAmountCent,
            long serviceFeeCent) {
    }

    record ProductOrderSummary(
            long orderCount,
            long attributedCount,
            long unattributedCount,
            long gmvCent,
            long serviceFeeCent,
            LocalDateTime lastOrderTime) {
    }

    record TalentOrderSummary(
            String talentUid,
            long orderCount,
            long orderAmountCent,
            long serviceFeeCent) {
    }

    record TalentRecentOrder(
            String orderId,
            String productName,
            long orderAmountCent,
            long serviceFeeCent,
            String channelName,
            LocalDateTime createTime) {
    }

    record DashboardReasonCount(String reason, long count) {
    }
}
