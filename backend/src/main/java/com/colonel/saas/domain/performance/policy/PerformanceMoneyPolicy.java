package com.colonel.saas.domain.performance.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 业绩双轨金额计算策略（DDD-PERF-002）。
 *
 * <p>纯计算策略，无 Spring 依赖。输入各活动分桶的金额与已解析的提成比例，
 * 输出双轨提成汇总（招商提成 + 渠道提成 + 毛利）。
 *
 * <p>计算公式：
 * <ul>
 *   <li>serviceFeeNet = max(serviceFeeIncome − techServiceFee − serviceFeeExpense, 0)</li>
 *   <li>bizCommission  = Σ multiplyCent(bucket.serviceFeeNet, bucket.bizRatio)</li>
 *   <li>channelCommission = Σ multiplyCent(bucket.serviceFeeNet, bucket.channelRatio)</li>
 *   <li>grossProfit = max(totalServiceFeeNet − bizCommission − channelCommission, 0)</li>
 * </ul>
 *
 * <p>调用方（{@code CommissionService}）负责从配置域与规则库解析比例后传入。
 */
public final class PerformanceMoneyPolicy {

    private PerformanceMoneyPolicy() {
    }

    /** 单个活动分桶的输入。 */
    public record BucketInput(
            long serviceFeeIncome,
            long techServiceFee,
            long serviceFeeExpense,
            long talentCommission,
            BigDecimal bizRatio,
            BigDecimal channelRatio) {
    }

    /** 双轨提成计算结果。 */
    public record MoneyResult(
            long serviceFeeIncome,
            long techServiceFee,
            long serviceFeeExpense,
            long talentCommission,
            long serviceFeeNet,
            long bizCommission,
            long channelCommission,
            long grossProfit,
            BigDecimal lastBizRatio,
            BigDecimal lastChannelRatio) {
    }

    /**
     * 按活动分桶计算双轨提成汇总。
     *
     * @param buckets 活动分桶列表（不可为 null），每个分桶须已解析 bizRatio / channelRatio
     * @return 汇总结果
     */
    public static MoneyResult calculate(List<BucketInput> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return emptyResult(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        long totalServiceFeeIncome = 0L;
        long totalTechServiceFee = 0L;
        long totalServiceFeeExpense = 0L;
        long totalTalentCommission = 0L;
        long bizCommission = 0L;
        long channelCommission = 0L;
        BigDecimal lastBizRatio = BigDecimal.ZERO;
        BigDecimal lastChannelRatio = BigDecimal.ZERO;

        for (BucketInput bucket : buckets) {
            totalServiceFeeIncome += Math.max(bucket.serviceFeeIncome(), 0L);
            totalTechServiceFee += Math.max(bucket.techServiceFee(), 0L);
            totalServiceFeeExpense += Math.max(bucket.serviceFeeExpense(), 0L);
            totalTalentCommission += Math.max(bucket.talentCommission(), 0L);

            long activityNet = activityServiceFeeNetCent(
                    bucket.serviceFeeIncome(), bucket.serviceFeeExpense(), bucket.techServiceFee());
            bizCommission += multiplyCent(activityNet, bucket.bizRatio());
            channelCommission += multiplyCent(activityNet, bucket.channelRatio());
            lastBizRatio = bucket.bizRatio();
            lastChannelRatio = bucket.channelRatio();
        }

        long serviceFeeNet = serviceFeeNetCent(totalServiceFeeIncome, totalTechServiceFee, totalServiceFeeExpense);
        long grossProfit = grossProfitCent(serviceFeeNet, bizCommission, channelCommission);

        return new MoneyResult(
                totalServiceFeeIncome,
                totalTechServiceFee,
                totalServiceFeeExpense,
                totalTalentCommission,
                serviceFeeNet,
                bizCommission,
                channelCommission,
                grossProfit,
                lastBizRatio,
                lastChannelRatio);
    }

    /** 服务费净收益 = 收入 − 技术服务费 − 服务费支出，不低于 0。 */
    public static long serviceFeeNetCent(long serviceFeeIncome, long techServiceFee, long serviceFeeExpense) {
        return Math.max(serviceFeeIncome - techServiceFee - serviceFeeExpense, 0L);
    }

    /** 活动级服务费净收益（参数顺序：收入 − 支出 − 技术费）。 */
    public static long activityServiceFeeNetCent(long serviceFeeIncome, long serviceFeeExpense, long techServiceFee) {
        return Math.max(serviceFeeIncome - serviceFeeExpense - techServiceFee, 0L);
    }

    /** 金额 × 比例（分精度，四舍五入）。 */
    public static long multiplyCent(long amount, BigDecimal ratio) {
        if (amount <= 0L || ratio == null) {
            return 0L;
        }
        return BigDecimal.valueOf(amount)
                .multiply(ratio)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /** 毛利 = 服务费收益 − 招商提成 − 渠道提成，不低于 0。 */
    public static long grossProfitCent(long serviceFeeNet, long bizCommission, long channelCommission) {
        return Math.max(serviceFeeNet - bizCommission - channelCommission, 0L);
    }

    private static MoneyResult emptyResult(BigDecimal bizRatio, BigDecimal channelRatio) {
        return new MoneyResult(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, bizRatio, channelRatio);
    }
}
