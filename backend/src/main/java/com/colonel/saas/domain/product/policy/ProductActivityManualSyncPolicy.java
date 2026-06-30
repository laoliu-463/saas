package com.colonel.saas.domain.product.policy;

/**
 * 活动商品手动同步触发状态策略。
 */
public final class ProductActivityManualSyncPolicy {

    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_INVALID = "INVALID";

    private static final String MESSAGE_ACCEPTED = "商品同步已转入后台执行";
    private static final String MESSAGE_RUNNING = "商品同步已在后台执行，请稍后刷新列表";

    private ProductActivityManualSyncPolicy() {
    }

    public static String messageFor(String syncStatus) {
        if (STATUS_RUNNING.equals(syncStatus)) {
            return MESSAGE_RUNNING;
        }
        return MESSAGE_ACCEPTED;
    }
}
