package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 操作日志分区清理定时任务。
 * <p>
 * 每日凌晨 2:30 执行，清理超过保留天数的操作日志数据库分区。
 * 操作日志采用分区表策略，按时间范围分区存储，过期分区通过 DROP PARTITION 方式清理，
 * 避免 DELETE 语句导致的表膨胀和性能下降。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：通过 {@code operation.log.cleanup.cron} 配置，默认每日凌晨 2:30</li>
 *   <li>保留天数：通过 {@code operation.log.retention-days} 配置，默认 90 天</li>
 *   <li>分布式锁 TTL：30 分钟</li>
 * </ul>
 * </p>
 *
 * @see OperationLogService#cleanupOldPartitions(int)
 * @see JobLockKeys#LOG_CLEANUP
 */
@Slf4j
@Component
public class LogCleanupJob {

    /** 分布式锁 TTL */
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    /** 操作日志服务 */
    private final OperationLogService operationLogService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;
    /** 日志保留天数，默认 90 天 */
    private final int retentionDays;

    public LogCleanupJob(
            OperationLogService operationLogService,
            DistributedJobLockService jobLockService,
            @Value("${operation.log.retention-days:90}") int retentionDays) {
        this.operationLogService = operationLogService;
        this.jobLockService = jobLockService;
        this.retentionDays = retentionDays;
    }

    /**
     * 清理超过保留期的操作日志分区。
     * <p>
     * 通过 DROP PARTITION 方式高效删除历史数据，不影响在线查询性能。
     * </p>
     */
    @Scheduled(cron = "${operation.log.cleanup.cron:0 30 2 * * ?}")
    public void cleanupOldPartitions() {
        if (!jobLockService.tryAcquire(JobLockKeys.LOG_CLEANUP, LOCK_TTL)) {
            log.info("LogCleanupJob skipped, another process is running");
            return;
        }
        try {
            int dropped = operationLogService.cleanupOldPartitions(retentionDays);
            log.info("LogCleanupJob completed, droppedPartitions={}, retentionDays={}", dropped, retentionDays);
        } catch (Exception ex) {
            log.error("LogCleanupJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.LOG_CLEANUP);
        }
    }
}
