package com.colonel.saas.job;

import com.colonel.saas.service.TalentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class TalentWeeklyRefreshJob {

    private final TalentService talentService;

    public TalentWeeklyRefreshJob(TalentService talentService) {
        this.talentService = talentService;
    }

    @Scheduled(cron = "0 0 3 ? * MON")
    public void weeklyRefreshActiveTalents() {
        List<UUID> talentIds = talentService.findActiveTalentIdsForRefresh();
        if (talentIds.isEmpty()) {
            log.info("Talent weekly refresh skipped, no active talents");
            return;
        }

        int success = 0;
        for (UUID talentId : talentIds) {
            try {
                talentService.refresh(talentId);
                success++;
            } catch (Exception ex) {
                log.error("Talent weekly refresh failed, talentId={}", talentId, ex);
            }
        }
        log.info("Talent weekly refresh completed, total={}, success={}", talentIds.size(), success);
    }
}

