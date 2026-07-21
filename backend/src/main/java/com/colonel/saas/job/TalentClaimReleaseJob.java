package com.colonel.saas.job;

import com.colonel.saas.domain.talent.application.TalentClaimReleaseApplicationService;
import com.colonel.saas.service.DistributedJobLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 达人认领到期自动释放定时任务。
 * <p>
 * 每日凌晨 2:15 执行，扫描所有已超过认领有效期的达人记录，
 * 将其状态重置为"未认领"，释放被锁定的达人资源供其他用户重新认领。
 * </p>
 * <p>
 * 业务背景：用户认领达人后有一个有效期窗口（如 30 天），
 * 有效期内该达人仅归认领者管理。到期后系统自动释放，避免资源长期被占。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：{@code 0 15 2 * * ?}（每日凌晨 2:15）</li>
 *   <li>分布式锁 TTL：30 分钟，充分覆盖大量记录扫描和更新的时间</li>
 *   <li>选择凌晨 2:15 执行，避开业务高峰期和订单同步等任务的时间窗口</li>
 * </ul>
 * </p>
 *
 * @see TalentClaimReleaseApplicationService#releaseExpiredClaims(LocalDateTime)
 * @see JobLockKeys#TALENT_CLAIM_RELEASE
 */
@Slf4j
@Component
public class TalentClaimReleaseJob {

    private static final String SCHEDULE_ZONE = "Asia/Shanghai";
    /** 分布式锁 TTL，30 分钟覆盖大批量更新场景 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    /** 达人认领释放应用服务 */
    private final TalentClaimReleaseApplicationService talentClaimReleaseApplicationService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;

    public TalentClaimReleaseJob(TalentClaimReleaseApplicationService talentClaimReleaseApplicationService,
                                 DistributedJobLockService jobLockService) {
        this.talentClaimReleaseApplicationService = talentClaimReleaseApplicationService;
        this.jobLockService = jobLockService;
    }

    /**
     * 释放所有已过期的达人认领记录。
     * <p>
     * 以当前时间为基准，将所有超过认领有效期的达人状态重置。
     * </p>
     */
    @Scheduled(cron = "0 15 2 * * ?", zone = SCHEDULE_ZONE)
    public void releaseExpiredClaimsDaily() {
        if (!jobLockService.tryAcquire(JobLockKeys.TALENT_CLAIM_RELEASE, LOCK_TTL)) {
            log.info("TalentClaimReleaseJob skipped, another process is running");
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            talentClaimReleaseApplicationService.releaseExpiredClaims(now);
            log.info("Talent claim release job completed at {}", now);
        } finally {
            jobLockService.release(JobLockKeys.TALENT_CLAIM_RELEASE);
        }
    }
}
