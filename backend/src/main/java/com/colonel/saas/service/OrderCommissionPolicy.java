package com.colonel.saas.service;

/**
 * 订单业绩/提成口径（Y-06：取消/失效不计入）。
 */
public final class OrderCommissionPolicy {

    /** 已取消 */
    public static final int STATUS_CANCELLED = 4;

    private OrderCommissionPolicy() {
    }

    public static boolean countsTowardCommission(Integer orderStatus) {
        if (orderStatus == null) {
            return true;
        }
        return orderStatus != STATUS_CANCELLED;
    }

    public static boolean countsTowardPerformance(Integer orderStatus) {
        return countsTowardCommission(orderStatus);
    }
}
