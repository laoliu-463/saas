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
    private final int refreshConcurrency;

    public TalentWeeklyRefreshJob(
            TalentService talentService,
            DistributedJobLockService jobLockService,
            @Qualifier("applicationTaskExecutor") Executor refreshExecutor,
            @Value("${talent.refresh.weekly-concurrency:4}") int refreshConcurrency) {
        this.talentService = talentService;
        this.jobLockService = jobLockService;
        this.refreshExecutor = refreshExecutor;
        this.refreshConcurrency = Math.max(1, refreshConcurrency);
    }

    @Scheduled(cron = "0 0 3 ? * MON")
    public void weeklyRefreshActiveTalents() {
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
            List<CompletableFuture<Boolean>> batch = new ArrayList<>();
            for (UUID talentId : talentIds) {
                batch.add(CompletableFuture.supplyAsync(() -> refreshSingleTalent(talentId), refreshExecutor));
                if (batch.size() >= refreshConcurrency) {
                    success += drainBatch(batch);
                    batch.clear();
                }
            }
            success += drainBatch(batch);
            log.info("Talent weekly refresh completed, total={}, success={}", talentIds.size(), success);
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

    private int drainBatch(List<CompletableFuture<Boolean>> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).join();
        int success = 0;
        for (CompletableFuture<Boolean> future : batch) {
            if (Boolean.TRUE.equals(future.join())) {
                success++;
            }
        }
        return success;
    }
}
