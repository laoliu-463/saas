package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.PerformanceBackfillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 业绩记录补录定时任务。
 * <p>
 * 每日凌晨 3:30 执行，扫描因订单同步延迟或系统异常而缺失业绩记录的订单，
 * 重新计算并补录业绩数据，确保业绩汇总的完整性。
 * </p>
 * <p>
 * 补录策略：
 * <ul>
 *   <li>仅处理缺失记录（{@code onlyMissing=true}），不覆盖已有业绩数据</li>
 *   <li>每次最多处理 500 条，避免凌晨批量处理对数据库造成过大压力</li>
 *   <li>时间段、类型等过滤参数均传 null，表示全量扫描</li>
 * </ul>
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：{@code 0 30 3 * * ?}（每日凌晨 3:30）</li>
 *   <li>分布式锁 TTL：30 分钟</li>
 *   <li>与 {@link PerformanceCacheWarmupJob}（3:00）和 {@link PerformanceRecalculateFailedJob} 错开执行</li>
 * </ul>
 * </p>
 *
 * @see PerformanceBackfillService#backfill(Integer, Integer, Integer, int, boolean)
 * @see JobLockKeys#PERFORMANCE_BACKFILL
 */
@Slf4j
@Component
public class PerformanceBackfillJob {

    /** 分布式锁 TTL */
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    /** 每次补录的最大记录数，防止批量操作对数据库造成压力 */
    private static final int NIGHTLY_LIMIT = 500;

    /** 业绩补录服务 */
    private final PerformanceBackfillService performanceBackfillService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;

    public PerformanceBackfillJob(
            PerformanceBackfillService performanceBackfillService,
            DistributedJobLockService jobLockService) {
        this.performanceBackfillService = performanceBackfillService;
        this.jobLockService = jobLockService;
    }

    /**
     * 补录缺失的业绩记录。
     * <p>
     * 全量扫描，仅处理缺失记录，限制每次处理上限为 500 条。
     * </p>
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void backfillMissingPerformanceRecords() {
        if (!jobLockService.tryAcquire(JobLockKeys.PERFORMANCE_BACKFILL, LOCK_TTL)) {
            log.info("PerformanceBackfillJob skipped, another process is running");
            return;
        }
        try {
            // backfill 参数说明：时间段/类型/null 表示不限制，onlyMissing=true 仅补录缺失
            PerformanceBackfillService.BackfillResult result = performanceBackfillService.backfill(
                    null,
                    null,
                    null,
                    NIGHTLY_LIMIT,
                    true);
            log.info(
                    "PerformanceBackfillJob completed, scanned={}, upserted={}, failed={}, onlyMissing={}",
                    result.scanned(),
                    result.upserted(),
                    result.failed(),
                    result.onlyMissing());
        } catch (Exception ex) {
            log.error("PerformanceBackfillJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.PERFORMANCE_BACKFILL);
        }
    }
}
