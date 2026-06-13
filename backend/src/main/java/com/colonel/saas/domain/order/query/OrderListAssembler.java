package com.colonel.saas.domain.order.query;

import com.colonel.saas.entity.ColonelsettlementOrder;
import org.springframework.beans.BeanUtils;

/**
 * 订单列表视图组装器（DDD-ORDER-006）。
 */
public class OrderListAssembler {

    public static OrderQueryView toView(ColonelsettlementOrder order) {
        if (order == null) {
            return null;
        }
        OrderQueryView view = new OrderQueryView();
        BeanUtils.copyProperties(order, view);
        return view;
    }
}
