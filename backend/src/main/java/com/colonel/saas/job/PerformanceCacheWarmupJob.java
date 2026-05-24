package com.colonel.saas.job;

import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.service.PerformanceSummaryService;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.performance.PerformanceAccessContext;
import com.colonel.saas.common.enums.DataScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnProperty(name = "performance.cache.warmup.enabled", havingValue = "true")
public class PerformanceCacheWarmupJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final PerformanceSummaryService performanceSummaryService;
    private final DistributedJobLockService jobLockService;

    @Value("${performance.cache.warmup.days:7}")
    private int warmupDays;

    public PerformanceCacheWarmupJob(
            PerformanceSummaryService performanceSummaryService,
            DistributedJobLockService jobLockService) {
        this.performanceSummaryService = performanceSummaryService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(cron = "${performance.cache.warmup.cron:0 0 3 * * ?}")
    public void warmup() {
        if (!jobLockService.tryAcquire(JobLockKeys.PERFORMANCE_CACHE_WARMUP, LOCK_TTL)) {
            log.info("PerformanceCacheWarmupJob skipped, another process is running");
            return;
        }
        try {
            LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();
            LocalDateTime start = LocalDate.now().minusDays(Math.max(warmupDays, 1) - 1L).atStartOfDay();
            PerformanceSummaryQuery query = new PerformanceSummaryQuery();
            query.setTimeFilterType("pay");
            query.setTimeStart(start);
            query.setTimeEnd(end);
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
