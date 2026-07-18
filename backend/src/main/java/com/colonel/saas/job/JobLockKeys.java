package com.colonel.saas.job;

/**
 * 定时任务分布式锁 key 常量类。
 * <p>
 * 集中管理所有定时任务（{@code @Scheduled}）在 Redis 中使用的分布式锁 key。
 * 每个 key 对应一个定时任务，格式为 {@code 域:功能:job:lock}。
 * 通过 {@link com.colonel.saas.service.DistributedJobLockService} 获取和释放锁，
 * 确保在多实例部署环境下同一时刻只有一个实例执行某个定时任务。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * if (!jobLockService.tryAcquire(JobLockKeys.ORDER_SYNC, LOCK_TTL)) {
 *     return; // 其他实例正在执行
 * }
 * try {
 *     // 执行任务逻辑
 * } finally {
 *     jobLockService.release(JobLockKeys.ORDER_SYNC);
 * }
 * </pre>
 * </p>
 *
 * @see com.colonel.saas.service.DistributedJobLockService
 */
public final class JobLockKeys {

    /** 抖音 OAuth Token 自动刷新任务锁 */
    public static final String DOUYIN_TOKEN_REFRESH = "douyin:token:refresh:job:lock";
    /** 达人数据每周全量刷新任务锁 */
    public static final String TALENT_WEEKLY_REFRESH = "talent:weekly:refresh:job:lock";
    /** 达人认领到期自动释放任务锁 */
    public static final String TALENT_CLAIM_RELEASE = "talent:claim:release:job:lock";
    /** 独家达人资格月度评估任务锁 */
    public static final String EXCLUSIVE_TALENT_EVALUATE = "exclusive:talent:evaluate:job:lock";
    /** 独家商家资格月度评估任务锁 */
    public static final String EXCLUSIVE_MERCHANT_EVALUATE = "exclusive:merchant:evaluate:job:lock";
    /** 寄样生命周期自动关闭任务锁 */
    public static final String SAMPLE_LIFECYCLE = "sample:lifecycle:job:lock";
    /** 物流轨迹刷新任务锁（LogisticsTrackJob 和 SampleLogisticsSyncJob 共用） */
    public static final String LOGISTICS_TRACK = "logistics:track:job:lock";
    /** 操作日志分区清理任务锁 */
    public static final String LOG_CLEANUP = "operation-log:cleanup:job:lock";
    /** 订单同步任务锁（默认增量 update 窗口） */
    public static final String ORDER_SYNC = "order:sync:lock";
    /**
     * 所有定时订单同步模式共享的外层互斥锁。
     *
     * <p>各模式仍保留自己的业务锁和水位；该锁只负责避免不同模式并发写入同一订单，
     * 防止调度线程同时占用大量堆内存并触发乐观锁冲突。手动同步不使用此锁。</p>
     */
    public static final String ORDER_SYNC_SCHEDULED_MUTEX = "order:sync:scheduled:mutex:lock";
    /** 订单事实源同步任务锁（6468 instituteOrderColonel）。与结算同步互不影响。 */
    public static final String ORDER_SYNC_INSTITUTE = "order:sync:institute:lock";
    /** 6468 近实时热同步任务锁（小窗口、高频）。与 {@link #ORDER_SYNC_INSTITUTE} 独立，避免阻塞补偿任务。 */
    public static final String ORDER_SYNC_INSTITUTE_HOT = "order:sync:institute:hot:lock";
    /** 订单同步近窗口（PAY_RECENT）补拉任务锁。与 {@link #ORDER_SYNC} 互不影响。 */
    public static final String ORDER_SYNC_PAY_RECENT = "order:sync:pay-recent:lock";
    /** 2704 结算时间轨（time_type=settle）独立同步任务锁。 */
    public static final String ORDER_SYNC_SETTLE = "order:sync:settle:lock";
    /** 业绩记录补录任务锁 */
    public static final String PERFORMANCE_BACKFILL = "performance:backfill:job:lock";
    /** 业绩汇总缓存预热任务锁 */
    public static final String PERFORMANCE_CACHE_WARMUP = "performance:cache:warmup:job:lock";
    /** 失败业绩记录重新计算任务锁 */
    public static final String PERFORMANCE_RECALCULATE_FAILED = "performance:recalculate-failed:job:lock";
    /** 商品展示状态规则对账任务锁 */
    public static final String PRODUCT_DISPLAY_RULE = "product:display:rule:job:lock";
    /** 商品置顶过期自动清除任务锁 */
    public static final String PRODUCT_PIN_EXPIRE = "product:pin:expire:job:lock";
    /** 活动商品快照定时同步任务锁 */
    public static final String PRODUCT_ACTIVITY_SYNC = "product:activity:sync:job:lock";
    /** 商品全量回补全局写锁（与定时同步、backfill 互斥）。 */
    public static final String PRODUCT_BACKFILL_GLOBAL = "product:backfill:global:job:lock";
    /** 单活动 backfill 写锁，key 模板：{@code product:backfill:activity:{activityId}:lock}。 */
    public static final String PRODUCT_BACKFILL_ACTIVITY_PREFIX = "product:backfill:activity:";
    /** 商品展示状态规则对账（{@code ProductDisplayRuleJob}）写锁，backfill 写完事实层后再触发它。 */
    public static final String PRODUCT_DISPLAY_REFRESH = "product:display:refresh:job:lock";
    /** 上校合作伙伴数据同步任务锁 */
    public static final String COLONEL_PARTNER_SYNC = "colonel:partner:sync:job:lock";
    /** 活动列表异步同步任务锁（独立于商品同步的 PRODUCT_ACTIVITY_SYNC 和 PRODUCT_BACKFILL_GLOBAL） */
    public static final String COLONEL_ACTIVITY_LIST_SYNC = "colonel:activity:list-sync:job:lock";
    /**
     * 单活动 backfill 写锁 key 生成。
     *
     * @param activityId 活动 ID
     * @return 完整 Redis 锁 key
     */
    public static String productBackfillActivityLock(String activityId) {
        return PRODUCT_BACKFILL_ACTIVITY_PREFIX + activityId + ":job:lock";
    }

    /**
     * 商品活动同步锁 key 前缀（P0-R2 修复）：
     * 手动同步与定时同步使用同一把 per-activity 锁，确保并发互斥。
     */
    public static final String PRODUCT_ACTIVITY_SYNC_LOCK_PREFIX = "product:activity:sync:activity:";

    /**
     * 商品活动同步锁 key（手动与定时共用）。
     */
    public static String productActivitySyncLock(String activityId) {
        return PRODUCT_ACTIVITY_SYNC_LOCK_PREFIX + activityId + ":job:lock";
    }
    /** 分区自动维护任务锁 */
    public static final String PARTITION_MAINTENANCE = "partition:maintenance:job:lock";

    /** 私有构造函数，防止实例化工具类 */
    private JobLockKeys() {
    }
}
