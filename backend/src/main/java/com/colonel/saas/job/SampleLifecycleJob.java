package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.SampleLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 寄样生命周期自动关闭定时任务。
 * <p>
 * 每日凌晨 2:00 执行，自动关闭超时的寄样申请记录：
 * <ul>
 *   <li><b>超时待交作业</b>：达人收到样品后未在规定时间内提交作业（如视频/图文内容）</li>
 *   <li><b>超时待发货</b>：商家批准寄样后未在规定时间内完成发货</li>
 * </ul>
 * </p>
 * <p>
 * 两个子任务相互独立，一个失败不影响另一个执行。
 * 这种容错设计确保单个子任务异常不会阻塞整个生命周期管理流程。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：{@code 0 0 2 * * ?}（每日凌晨 2:00）</li>
 *   <li>分布式锁 TTL：30 分钟</li>
 *   <li>与 {@link TalentClaimReleaseJob}（2:15）错开 15 分钟，避免凌晨任务集中</li>
 * </ul>
 * </p>
 *
 * @see SampleLifecycleService#autoCloseTimeoutPendingHomework()
 * @see SampleLifecycleService#autoCloseTimeoutPendingShip()
 * @see JobLockKeys#SAMPLE_LIFECYCLE
 */
@Slf4j
@Component
public class SampleLifecycleJob {

    /** 分布式锁 TTL */
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    /** 寄样生命周期服务 */
    private final SampleLifecycleService sampleLifecycleService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;

    public SampleLifecycleJob(
            SampleLifecycleService sampleLifecycleService,
            DistributedJobLockService jobLockService) {
        this.sampleLifecycleService = sampleLifecycleService;
        this.jobLockService = jobLockService;
    }

    /**
     * 自动关闭超时的寄样请求。
     * <p>
     * 依次处理超时待交作业和超时待发货两类场景，
     * 两个子任务各自捕获异常，互不影响。
     * </p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void autoCloseTimeoutRequests() {
        if (!jobLockService.tryAcquire(JobLockKeys.SAMPLE_LIFECYCLE, LOCK_TTL)) {
            log.info("SampleLifecycleJob skipped, another process is running");
            return;
        }
        try {
            // 子任务 1：关闭超时未交作业的寄样记录
            try {
                int closedHomework = sampleLifecycleService.autoCloseTimeoutPendingHomework();
                log.info("SampleLifecycleJob auto close homework completed, closed={}", closedHomework);
            } catch (Exception ex) {
                log.error("SampleLifecycleJob auto close homework failed", ex);
            }
            // 子任务 2：关闭超时未发货的寄样记录
            try {
                int closedShip = sampleLifecycleService.autoCloseTimeoutPendingShip();
                log.info("SampleLifecycleJob auto close ship completed, closed={}", closedShip);
            } catch (Exception ex) {
                log.error("SampleLifecycleJob auto close ship failed", ex);
            }
        } finally {
            jobLockService.release(JobLockKeys.SAMPLE_LIFECYCLE);
        }
    }
}
