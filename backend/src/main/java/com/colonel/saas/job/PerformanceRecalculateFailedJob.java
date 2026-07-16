package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.PerformanceBackfillService;
import com.colonel.saas.domain.performance.application.PerformanceCalculationRetryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 失败业绩记录重新计算定时任务。
 * <p>
 * 优先按持久化执行台账重试失败事件，再保留旧的缺失业绩补录兜底。
 * 与 {@link PerformanceBackfillJob} 的区别在于：本任务专注于失败事件，
 * 而补录任务专注于扫描和补充缺失的记录。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：通过 {@code performance.recalculate-failed.cron} 配置，默认每 10 分钟</li>
 *   <li>默认启用：失败执行台账必须在受控调度中恢复，不能仅依赖进程内日志</li>
 *   <li>分布式锁 TTL：10 分钟</li>
 *   <li>每次最多处理数量通过 {@code performance.recalculate-failed.limit} 配置，默认 100 条</li>
 * </ul>
 * </p>
 *
 * @see PerformanceBackfillService#backfill(Integer, Integer, Integer, int, boolean)
 * @see PerformanceBackfillJob
 * @see JobLockKeys#PERFORMANCE_RECALCULATE_FAILED
 */
@Slf4j
@Component
public class PerformanceRecalculateFailedJob {

    /** 分布式锁 TTL */
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    /** 业绩补录服务（复用 backfill 逻辑） */
    private final PerformanceBackfillService performanceBackfillService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;
    /** 基于事件台账的精确重试服务。 */
    private final PerformanceCalculationRetryService calculationRetryService;

    /** 每次重算的最大记录数，默认 100 */
    @Value("${performance.recalculate-failed.limit:100}")
    private int limit;

    public PerformanceRecalculateFailedJob(
            PerformanceBackfillService performanceBackfillService,
            DistributedJobLockService jobLockService,
            PerformanceCalculationRetryService calculationRetryService) {
        this.performanceBackfillService = performanceBackfillService;
        this.jobLockService = jobLockService;
        this.calculationRetryService = calculationRetryService;
    }

    /**
     * 重新计算失败的业绩记录。
     * <p>
     * 复用补录服务的 backfill 方法，通过 onlyMissing=true 参数仅处理失败记录。
     * </p>
     */
    @Scheduled(cron = "${performance.recalculate-failed.cron:0 */10 * * * ?}")
    public void recalculateFailed() {
        if (!jobLockService.tryAcquire(JobLockKeys.PERFORMANCE_RECALCULATE_FAILED, LOCK_TTL)) {
            log.info("PerformanceRecalculateFailedJob skipped, another process is running");
            return;
        }
        try {
            PerformanceCalculationRetryService.RetryResult retryResult = calculationRetryService.retryDue(limit);
            PerformanceBackfillService.BackfillResult result = performanceBackfillService.backfill(
                    null,
                    null,
                    null,
                    limit,
                    true);
            log.info(
                    "PerformanceRecalculateFailedJob completed, eventAttempted={}, eventSucceeded={}, eventFailed={}, "
                            + "scanned={}, upserted={}, failed={}",
                    retryResult.attempted(),
                    retryResult.succeeded(),
                    retryResult.failed(),
                    result.scanned(),
                    result.upserted(),
                    result.failed());
        } catch (Exception ex) {
            log.error("PerformanceRecalculateFailedJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.PERFORMANCE_RECALCULATE_FAILED);
        }
    }
}
