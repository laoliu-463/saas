package com.colonel.saas.job;

import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.service.PerformanceSummaryService;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.common.enums.DataScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业绩汇总缓存预热定时任务。
 * <p>
 * 每日凌晨 3 点执行，主动将近期的业绩汇总数据加载到缓存中，
 * 避免白天业务高峰时段的首次查询触发缓存穿透，提升用户访问体验。
 * </p>
 * <p>
 * 预热策略：
 * <ul>
 *   <li>默认预热最近 7 天的业绩汇总数据（可配置 {@code performance.cache.warmup.days}）</li>
 *   <li>时间范围：从 {@code warmupDays} 天前到今天 23:59:59（即明天 00:00:00）</li>
 *   <li>使用付款时间（{@code pay}）作为时间过滤类型</li>
 *   <li>以 {@link DataScope#ALL} 全量数据范围查询，确保缓存覆盖所有维度</li>
 * </ul>
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：{@code 0 0 3 * * ?}（每日凌晨 3 点，可配置）</li>
 *   <li>分布式锁 TTL：10 分钟</li>
 *   <li>默认禁用，需通过 {@code performance.cache.warmup.enabled=true} 显式开启</li>
 * </ul>
 * </p>
 *
 * @see PerformanceSummaryService#getSummary(PerformanceSummaryQuery, PerformanceAccessContext)
 * @see JobLockKeys#PERFORMANCE_CACHE_WARMUP
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "performance.cache.warmup.enabled", havingValue = "true")
public class PerformanceCacheWarmupJob {

    /** 分布式锁 TTL */
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    /** 业绩汇总服务 */
    private final PerformanceSummaryService performanceSummaryService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;

    /** 缓存预热的天数范围，默认 7 天 */
    @Value("${performance.cache.warmup.days:7}")
    private int warmupDays;

    /**
     * 构造函数，注入依赖。
     *
     * @param performanceSummaryService 业绩汇总服务
     * @param jobLockService 分布式锁服务
     */
    public PerformanceCacheWarmupJob(
            PerformanceSummaryService performanceSummaryService,
            DistributedJobLockService jobLockService) {
        this.performanceSummaryService = performanceSummaryService;
        this.jobLockService = jobLockService;
    }

    /**
     * 执行业绩缓存预热。
     * <p>
     * 计算预热时间范围（最近 N 天到今天），构造查询条件并调用业绩汇总服务，
     * 触发缓存写入。预热使用全量数据范围，确保缓存对所有用户角色可用。
     * </p>
     */
    @Scheduled(cron = "${performance.cache.warmup.cron:0 0 3 * * ?}")
    public void warmup() {
        if (!jobLockService.tryAcquire(JobLockKeys.PERFORMANCE_CACHE_WARMUP, LOCK_TTL)) {
            log.info("PerformanceCacheWarmupJob skipped, another process is running");
            return;
        }
        try {
            // 计算预热时间范围：warmupDays 天前的 00:00:00 到明天的 00:00:00
            LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();
            LocalDateTime start = LocalDate.now().minusDays(Math.max(warmupDays, 1) - 1L).atStartOfDay();
            // 构造查询条件，以付款时间为过滤维度
            PerformanceSummaryQuery query = new PerformanceSummaryQuery();
            query.setTimeFilterType("pay");
            query.setTimeStart(start);
            query.setTimeEnd(end);
            // 使用全量数据范围，确保缓存覆盖所有用户角色
            PerformanceAccessContext context = PerformanceAccessContext.of(
                    null, null, DataScope.ALL, java.util.List.of());
            performanceSummaryService.getSummary(query, context);
            log.info("PerformanceCacheWarmupJob completed, range={} ~ {}", start, end);
        } catch (Exception ex) {
            log.error("PerformanceCacheWarmupJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.PERFORMANCE_CACHE_WARMUP);
        }
    }
}
