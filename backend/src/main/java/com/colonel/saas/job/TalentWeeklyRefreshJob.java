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

@Slf4j
@Component
public class TalentWeeklyRefreshJob {

    private static final Duration LOCK_TTL = Duration.ofHours(2);

    private final TalentService talentService;
    private final DistributedJobLockService jobLockService;
    private final Executor refreshExecutor;
    private final boolean refreshEnabled;
    private final int refreshConcurrency;
    private final int batchSize;

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

    @Scheduled(cron = "${talent.refresh.cron:0 0 3 ? * MON}")
    public void weeklyRefreshActiveTalents() {
        if (!refreshEnabled) {
            log.debug("TalentWeeklyRefreshJob skipped, talent.refresh.enabled=false");
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.TALENT_WEEKLY_REFRESH, LOCK_TTL)) {
            log.info("TalentWeeklyRefreshJob skipped, another process is running");
            return;
        }
        try {
            List<UUID> talentIds = talentService.findActiveTalentIdsForRefresh();
            if (talentIds.isEmpty()) {
                log.info("Talent weekly refresh skipped, no active talents");
                return;
            }

            int success = 0;
            int failed = 0;
            for (int offset = 0; offset < talentIds.size(); offset += batchSize) {
                int end = Math.min(offset + batchSize, talentIds.size());
                List<UUID> slice = talentIds.subList(offset, end);
                List<CompletableFuture<Boolean>> batch = new ArrayList<>();
                for (UUID talentId : slice) {
                    batch.add(CompletableFuture.supplyAsync(() -> refreshSingleTalent(talentId), refreshExecutor));
                    if (batch.size() >= refreshConcurrency) {
                        int[] counts = drainBatch(batch);
                        success += counts[0];
                        failed += counts[1];
                        batch.clear();
                    }
                }
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

    private boolean refreshSingleTalent(UUID talentId) {
        try {
            talentService.refresh(talentId);
            return true;
        } catch (Exception ex) {
            log.error("Talent weekly refresh failed, talentId={}", talentId, ex);
            return false;
        }
    }

    private int[] drainBatch(List<CompletableFuture<Boolean>> batch) {
        if (batch.isEmpty()) {
            return new int[] {0, 0};
        }
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
