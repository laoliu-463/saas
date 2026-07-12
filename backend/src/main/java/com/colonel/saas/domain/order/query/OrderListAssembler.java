package com.colonel.saas.domain.order.query;

import com.colonel.saas.entity.ColonelsettlementOrder;

/**
 * 订单列表视图组装器（DDD-ORDER-006）。
 */
public class OrderListAssembler {

    public static OrderQueryView toView(ColonelsettlementOrder order) {
        if (order == null) {
            return null;
        }
        OrderQueryView view = new OrderQueryView();
        BeanPropertyCopy.copy(order, view);
        // 双团长订单可能只有第二活动；API 的 activityId 仍需暴露可追溯的有效活动事实。
        if (!hasText(view.getActivityId())) {
            view.setActivityId(view.getSecondActivityId());
        }
        view.setOrderAmount(zeroIfNull(view.getOrderAmount()));
        view.setPayAmount(view.getOrderAmount());
        view.setActualAmount(zeroIfNull(view.getActualAmount()));
        view.setSettleAmount(zeroIfNull(view.getSettleAmount()));
        view.setEstimateServiceFee(zeroIfNull(view.getEstimateServiceFee()));
        view.setEffectiveServiceFee(zeroIfNull(view.getEffectiveServiceFee()));
        view.setEstimateTechServiceFee(zeroIfNull(view.getEstimateTechServiceFee()));
        view.setEffectiveTechServiceFee(zeroIfNull(view.getEffectiveTechServiceFee()));
        view.setEstimateServiceFeeExpense(zeroIfNull(view.getEstimateServiceFeeExpense()));
        view.setEffectiveServiceFeeExpense(zeroIfNull(view.getEffectiveServiceFeeExpense()));
        return view;
    }

    private static Long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
