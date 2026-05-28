package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.PerformanceBackfillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 失败业绩记录重新计算定时任务。
 * <p>
 * 针对之前计算失败（如上游数据不完整、临时异常等）的业绩记录进行重新计算。
 * 与 {@link PerformanceBackfillJob} 的区别在于：本任务专注于重试失败的记录，
 * 而补录任务专注于扫描和补充缺失的记录。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：通过 {@code performance.recalculate-failed.cron} 配置，默认每 10 分钟</li>
 *   <li>默认禁用，需通过 {@code performance.recalculate-failed.enabled=true} 显式开启</li>
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
@ConditionalOnProperty(name = "performance.recalculate-failed.enabled", havingValue = "true")
public class PerformanceRecalculateFailedJob {

    /** 分布式锁 TTL */
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    /** 业绩补录服务（复用 backfill 逻辑） */
    private final PerformanceBackfillService performanceBackfillService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;

    /** 每次重算的最大记录数，默认 100 */
    @Value("${performance.recalculate-failed.limit:100}")
    private int limit;

    public PerformanceRecalculateFailedJob(
            PerformanceBackfillService performanceBackfillService,
            DistributedJobLockService jobLockService) {
        this.performanceBackfillService = performanceBackfillService;
        this.jobLockService = jobLockService;
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
            PerformanceBackfillService.BackfillResult result = performanceBackfillService.backfill(
                    null,
                    null,
                    null,
                    limit,
                    true);
            log.info(
                    "PerformanceRecalculateFailedJob completed, scanned={}, upserted={}, failed={}",
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
