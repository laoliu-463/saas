package com.colonel.saas.domain.order.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.product.query.ActivityProductOrderReadRepository;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;

import java.util.List;
import java.util.Set;

/**
 * MyBatis-backed 活动商品订单汇总读侧适配器。
 */
public class ActivityProductOrderMapperReadRepository implements ActivityProductOrderReadRepository {

    private final ColonelsettlementOrderMapper orderMapper;

    public ActivityProductOrderMapperReadRepository(ColonelsettlementOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public List<ColonelsettlementOrder> findOrders(String activityId, Set<String> productIds) {
        return orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getActivityId, activityId)
                .in(ColonelsettlementOrder::getProductId, productIds)
                .orderByDesc(ColonelsettlementOrder::getCreateTime));
    }

    @Override
    public List<ColonelsettlementOrder> findOrders(String activityId, String productId) {
        return orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getActivityId, activityId)
                .eq(ColonelsettlementOrder::getProductId, productId)
                .orderByDesc(ColonelsettlementOrder::getCreateTime));
    }
}
