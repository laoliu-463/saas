package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.TalentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 达人数据每周刷新定时任务。
 * <p>
 * 每周一凌晨 3 点执行，从抖音平台批量刷新活跃达人的最新数据（如粉丝数、
 * 带货数据、合作状态等），确保本地达人数据与平台保持同步。
 * </p>
 * <p>
 * 并发处理策略：
 * <ul>
 *   <li>采用分批 + 并发模式：将达人 ID 列表按 {@code batchSize} 分片</li>
 *   <li>每片内使用 {@link CompletableFuture} 并发刷新，并发度由 {@code refreshConcurrency} 控制</li>
 *   <li>默认并发度 4，批次大小 50，避免并发过高导致平台限流</li>
 *   <li>单个达人刷新失败不影响同批次其他达人，仅记录错误日志</li>
 * </ul>
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：{@code 0 0 3 ? * MON}（每周一凌晨 3 点，可配置）</li>
 *   <li>分布式锁 TTL：2 小时，全量刷新可能耗时较长</li>
 *   <li>默认禁用，需通过 {@code talent.refresh.enabled=true} 显式开启</li>
 * </ul>
 * </p>
 *
 * @see TalentService#findActiveTalentIdsForRefresh()
 * @see TalentService#refresh(UUID)
 * @see JobLockKeys#TALENT_WEEKLY_REFRESH
 */
@Slf4j
@Component
public class TalentWeeklyRefreshJob {

    /** 分布式锁 TTL，全量刷新可能耗时较长，设置为 2 小时 */
    private static final Duration LOCK_TTL = Duration.ofHours(2);

    /** 达人服务 */
    private final TalentService talentService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;
    /** 异步执行线程池，用于并发刷新达人数据 */
    private final Executor refreshExecutor;
    /** 是否启用达人每周刷新 */
    private final boolean refreshEnabled;
    /** 并发刷新的最大线程数 */
    private final int refreshConcurrency;
    /** 每批处理的达人数量 */
    private final int batchSize;

    /**
     * 构造函数，注入依赖并读取配置。
     *
     * @param talentService 达人服务
     * @param jobLockService 分布式锁服务
     * @param refreshExecutor 异步执行线程池（使用 Spring 默认的应用任务执行器）
     * @param refreshEnabled 是否启用达人刷新，默认 false
     * @param refreshConcurrency 并发刷新线程数，默认 4，最小 1
     * @param batchSize 每批处理的达人数量，默认 50，最小 1
     */
    public TalentWeeklyRefreshJob(
            TalentService talentService,
            DistributedJobLockService jobLockService,
            @Qualifier("applicationTaskExecutor") Executor refreshExecutor,
            @Value("${talent.refresh.enabled:false}") boolean refreshEnabled,
            @Value("${talent.refresh.weekly-concurrency:4}") int refreshConcurrency,
            @Value("${talent.refresh.batch-size:50}") int batchSize) {
        this.talentService = talentService;
        this.jobLockService = jobLockService;
        this.refreshExecutor = refreshExecutor;
        this.refreshEnabled = refreshEnabled;
        this.refreshConcurrency = Math.max(1, refreshConcurrency);
        this.batchSize = Math.max(1, batchSize);
    }

    /**
     * 执行每周达人数据刷新。
     * <p>
     * 主流程：
     * <ol>
     *   <li>检查功能开关，未启用则跳过</li>
     *   <li>获取分布式锁，防止多实例重复刷新</li>
     *   <li>查询所有需要刷新的活跃达人 ID 列表</li>
     *   <li>按 {@code batchSize} 分片，每片内并发刷新</li>
     *   <li>累积统计成功和失败数量，记录批次和总计日志</li>
     * </ol>
     * </p>
     */
    @Scheduled(cron = "${talent.refresh.cron:0 0 3 ? * MON}")
    public void weeklyRefreshActiveTalents() {
        // 检查功能开关
        if (!refreshEnabled) {
            log.debug("TalentWeeklyRefreshJob skipped, talent.refresh.enabled=false");
            return;
        }
        // 获取分布式锁，2 小时超时
        if (!jobLockService.tryAcquire(JobLockKeys.TALENT_WEEKLY_REFRESH, LOCK_TTL)) {
            log.info("TalentWeeklyRefreshJob skipped, another process is running");
            return;
        }
        try {
            // 查询所有需要刷新的活跃达人 ID
            List<UUID> talentIds = talentService.findActiveTalentIdsForRefresh();
            if (talentIds.isEmpty()) {
                log.info("Talent weekly refresh skipped, no active talents");
                return;
            }

            int success = 0;
            int failed = 0;
            // 按批次分片处理
            for (int offset = 0; offset < talentIds.size(); offset += batchSize) {
                int end = Math.min(offset + batchSize, talentIds.size());
                List<UUID> slice = talentIds.subList(offset, end);
                List<CompletableFuture<Boolean>> batch = new ArrayList<>();
                for (UUID talentId : slice) {
                    // 提交异步刷新任务
                    batch.add(CompletableFuture.supplyAsync(() -> refreshSingleTalent(talentId), refreshExecutor));
                    // 达到并发上限时，阻塞等待当前批次完成
                    if (batch.size() >= refreshConcurrency) {
                        int[] counts = drainBatch(batch);
                        success += counts[0];
                        failed += counts[1];
                        batch.clear();
                    }
                }
                // 处理最后一个不足并发上限的批次
                int[] counts = drainBatch(batch);
                success += counts[0];
                failed += counts[1];
                log.info(
                        "Talent weekly refresh batch done, batchOffset={}, batchSize={}, sliceSuccess={}, sliceFailed={}",
                        offset,
                        slice.size(),
                        counts[0],
                        counts[1]);
            }
            log.info(
                    "Talent weekly refresh completed, total={}, success={}, failed={}",
                    talentIds.size(),
                    success,
                    failed);
        } catch (Exception ex) {
            log.error("Talent weekly refresh job failed unexpectedly", ex);
        } finally {
            jobLockService.release(JobLockKeys.TALENT_WEEKLY_REFRESH);
        }
    }

    /**
     * 刷新单个达人的数据。
     * <p>
     * 调用达人服务的刷新方法，成功返回 true，异常时记录错误日志并返回 false。
     * 单个达人的失败不会影响其他达人。
     * </p>
     *
     * @param talentId 达人 ID
     * @return 刷新是否成功
     */
    private boolean refreshSingleTalent(UUID talentId) {
        try {
            talentService.refresh(talentId);
            return true;
        } catch (Exception ex) {
            log.error("Talent weekly refresh failed, talentId={}", talentId, ex);
            return false;
        }
    }

    /**
     * 阻塞等待一批异步任务全部完成，并统计成功和失败数量。
     * <p>
     * 使用 {@link CompletableFuture#allOf} 等待所有任务完成后遍历结果。
     * </p>
     *
     * @param batch 待等待的异步任务列表
     * @return int[] 数组，[0]=成功数，[1]=失败数
     */
    private int[] drainBatch(List<CompletableFuture<Boolean>> batch) {
        if (batch.isEmpty()) {
            return new int[] {0, 0};
        }
        // 阻塞等待所有任务完成
        CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).join();
        int success = 0;
        int failed = 0;
        for (CompletableFuture<Boolean> future : batch) {
            if (Boolean.TRUE.equals(future.join())) {
                success++;
            } else {
                failed++;
            }
        }
        return new int[] {success, failed};
    }
}
