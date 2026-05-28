package com.colonel.saas.job;

import com.colonel.saas.service.ColonelPartnerSyncService;
import com.colonel.saas.service.DistributedJobLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 上校合作伙伴数据同步定时任务。
 * <p>
 * 每 30 分钟执行一次，从抖音电商平台同步合作伙伴（达人/商家）的最新数据。
 * 同步内容包括合作伙伴的基本信息、合作状态、关联商品等。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：{@code 0 30 * * * ?}（每小时的第 30 分钟执行）</li>
 *   <li>分布式锁 TTL：25 分钟，确保在下一次调度前完成或超时释放</li>
 *   <li>全量同步模式，由 {@link ColonelPartnerSyncService#syncAll()} 内部处理增量逻辑</li>
 * </ul>
 * </p>
 *
 * @see ColonelPartnerSyncService#syncAll()
 * @see JobLockKeys#COLONEL_PARTNER_SYNC
 */
@Slf4j
@Component
public class ColonelPartnerSyncJob {

    /** 分布式锁 TTL，25 分钟确保在下一次调度前完成 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(25);

    /** 合作伙伴同步服务 */
    private final ColonelPartnerSyncService colonelPartnerSyncService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;

    public ColonelPartnerSyncJob(
            ColonelPartnerSyncService colonelPartnerSyncService,
            DistributedJobLockService jobLockService) {
        this.colonelPartnerSyncService = colonelPartnerSyncService;
        this.jobLockService = jobLockService;
    }

    /**
     * 同步所有合作伙伴数据。
     * <p>
     * 从抖音电商平台拉取最新合作伙伴数据并更新本地数据库。
     * </p>
     */
    @Scheduled(cron = "0 30 * * * ?")
    public void syncPartners() {
        if (!jobLockService.tryAcquire(JobLockKeys.COLONEL_PARTNER_SYNC, LOCK_TTL)) {
            log.info("ColonelPartnerSyncJob skipped, another process is running");
            return;
        }
        try {
            int upserted = colonelPartnerSyncService.syncAll();
            log.info("ColonelPartnerSyncJob completed, upserted={}", upserted);
        } catch (Exception ex) {
            log.error("ColonelPartnerSyncJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.COLONEL_PARTNER_SYNC);
        }
    }
}
