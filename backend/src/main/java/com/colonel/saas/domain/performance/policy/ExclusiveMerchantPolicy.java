package com.colonel.saas.domain.performance.policy;

import java.math.BigDecimal;

/**
 * 独家商家业务判定策略（DDD-PERF-005）。
 *
 * <p>纯业务规则：当服务费占比 >= 指定阈值时，达成独家合作。</p>
 */
public final class ExclusiveMerchantPolicy {

    private ExclusiveMerchantPolicy() {
    }

    /**
     * 判定是否满足独家商家条件。
     *
     * @param ratio     实际占比（百分比）
     * @param threshold 判定阈值（百分比）
     * @return true 表示满足独家商家条件
     */
    public static boolean meets(BigDecimal ratio, BigDecimal threshold) {
        if (ratio == null || threshold == null) {
            return false;
        }
        return ratio.compareTo(threshold) >= 0;
    }
}
