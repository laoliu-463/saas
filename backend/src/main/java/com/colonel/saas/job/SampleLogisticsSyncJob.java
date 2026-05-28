package com.colonel.saas.job;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.SampleLogisticsSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 寄样物流同步定时任务。
 * <p>
 * 每 30 分钟执行一次，扫描所有处于"待发货-在途"状态的寄样记录，
 * 从物流平台拉取最新的物流轨迹信息并更新到本地数据库。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：默认每 30 分钟执行一次，可通过 {@code logistics.sync.cron} 配置</li>
 *   <li>分布式锁 TTL：30 分钟，与调度周期一致</li>
 *   <li>批次大小通过 {@link LogisticsProperties} 配置，避免单次同步过多记录</li>
 *   <li>测试模式下自动跳过（{@code app.test.enabled=true}）</li>
 *   <li>可通过 {@code logistics.sync.enabled=false} 关闭此任务</li>
 * </ul>
 * </p>
 *
 * @see SampleLogisticsSyncService#syncPendingInTransit(int)
 * @see JobLockKeys#LOGISTICS_TRACK
 */
@Slf4j
@Component
public class SampleLogisticsSyncJob {

    /** 分布式锁 TTL，30 分钟与调度周期一致 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    /** 寄样物流同步服务 */
    private final SampleLogisticsSyncService sampleLogisticsSyncService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;
    /** 物流配置属性 */
    private final LogisticsProperties logisticsProperties;
    /** 是否为测试模式 */
    private final boolean testEnabled;

    /**
     * 构造函数，注入依赖。
     *
     * @param sampleLogisticsSyncService 寄样物流同步服务
     * @param jobLockService 分布式锁服务
     * @param logisticsProperties 物流配置属性（含批次大小、开关等）
     * @param testEnabled 是否为测试模式，测试模式下跳过同步
     */
    public SampleLogisticsSyncJob(
            SampleLogisticsSyncService sampleLogisticsSyncService,
            DistributedJobLockService jobLockService,
            LogisticsProperties logisticsProperties,
            @Value("${app.test.enabled:false}") boolean testEnabled) {
        this.sampleLogisticsSyncService = sampleLogisticsSyncService;
        this.jobLockService = jobLockService;
        this.logisticsProperties = logisticsProperties;
        this.testEnabled = testEnabled;
    }

    /**
     * 同步在途寄样的物流信息。
     * <p>
     * 先检查物流同步功能是否启用和是否处于测试模式，
     * 然后通过分布式锁保证多实例环境下的单点执行，
     * 最后调用同步服务拉取物流轨迹并更新数据库。
     * </p>
     */
    @Scheduled(cron = "${logistics.sync.cron:0 */30 * * * ?}")
    public void syncInTransitSamples() {
        // 检查物流同步功能是否启用
        if (!logisticsProperties.getSync().isEnabled()) {
            return;
        }
        // 测试模式下跳过，避免测试环境调用外部物流 API
        if (testEnabled) {
            return;
        }
        // 获取分布式锁，防止多实例重复同步
        if (!jobLockService.tryAcquire(JobLockKeys.LOGISTICS_TRACK, LOCK_TTL)) {
            log.info("SampleLogisticsSyncJob skipped, lock held");
            return;
        }
        try {
            // 按配置的批次大小同步待处理的在途寄样
            SampleLogisticsSyncService.SyncBatchSummary summary =
                    sampleLogisticsSyncService.syncPendingInTransit(logisticsProperties.getSync().getBatchSize());
            log.info("SampleLogisticsSyncJob done: total={}, success={}, failed={}, skipped={}",
                    summary.total(), summary.success(), summary.failed(), summary.skipped());
        } finally {
            jobLockService.release(JobLockKeys.LOGISTICS_TRACK);
        }
    }
}
