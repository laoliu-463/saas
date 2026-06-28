package com.colonel.saas.domain.product.query;

import com.colonel.saas.entity.ColonelsettlementOrder;

import java.util.List;
import java.util.Set;

/**
 * 活动商品订单汇总读侧端口。
 */
public interface ActivityProductOrderReadRepository {

    List<ColonelsettlementOrder> findOrders(String activityId, Set<String> productIds);

    List<ColonelsettlementOrder> findOrders(String activityId, String productId);
}
