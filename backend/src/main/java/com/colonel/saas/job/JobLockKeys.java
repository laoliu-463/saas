package com.colonel.saas.job;

/**
 * 定时任务分布式锁 key 常量。
 */
public final class JobLockKeys {

    public static final String DOUYIN_TOKEN_REFRESH = "douyin:token:refresh:job:lock";
    public static final String TALENT_WEEKLY_REFRESH = "talent:weekly:refresh:job:lock";
    public static final String TALENT_CLAIM_RELEASE = "talent:claim:release:job:lock";
    public static final String EXCLUSIVE_TALENT_EVALUATE = "exclusive:talent:evaluate:job:lock";
    public static final String EXCLUSIVE_MERCHANT_EVALUATE = "exclusive:merchant:evaluate:job:lock";
    public static final String SAMPLE_LIFECYCLE = "sample:lifecycle:job:lock";
    public static final String LOGISTICS_TRACK = "logistics:track:job:lock";
    public static final String LOG_CLEANUP = "operation-log:cleanup:job:lock";
    public static final String ORDER_SYNC = "order:sync:lock";
    public static final String PERFORMANCE_BACKFILL = "performance:backfill:job:lock";

    private JobLockKeys() {
    }
}
