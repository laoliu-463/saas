package com.colonel.saas.domain.talent.policy;

import java.math.BigDecimal;

/**
 * 独家达人判定策略（DDD-TALENT-004）。
 *
 * <p>双阈值必须同时满足：服务费占比 + 月寄样数量。</p>
 *
 * <ul>
 *   <li>服务费占比 ≥ {@code ratioThreshold}</li>
 *   <li>有效寄样数（已发货）≥ {@code sampleThreshold}</li>
 * </ul>
 */
public final class ExclusiveTalentPolicy {

    private ExclusiveTalentPolicy() {
    }

    public static boolean meets(BigDecimal ratio, BigDecimal ratioThreshold,
                                 int sampleCount, int sampleThreshold) {
        if (ratio == null || ratioThreshold == null) {
            return false;
        }
        if (ratio.compareTo(ratioThreshold) < 0) {
            return false;
        }
        return sampleCount >= sampleThreshold;
    }

    /**
     * 计算某渠道-达人组合的服务费占比（百分比，保留 2 位小数）。
     */
    public static BigDecimal computeRatio(long channelFee, long totalFee) {
        if (totalFee <= 0L) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(channelFee)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalFee), 2, java.math.RoundingMode.HALF_UP);
    }
}