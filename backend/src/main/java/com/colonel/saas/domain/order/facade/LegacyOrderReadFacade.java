package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.OrderCommissionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * {@link OrderReadFacade} 遗留实现：委派现有 {@link ColonelsettlementOrderMapper}，零行为变更。
 */
@Service
public class LegacyOrderReadFacade implements OrderReadFacade {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 2000;

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

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
