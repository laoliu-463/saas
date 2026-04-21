package com.colonel.saas.job;

import com.colonel.saas.service.SampleLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SampleLifecycleJob {

    private final SampleLifecycleService sampleLifecycleService;

    public SampleLifecycleJob(SampleLifecycleService sampleLifecycleService) {
        this.sampleLifecycleService = sampleLifecycleService;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void autoCloseTimeoutRequests() {
        try {
            int closed = sampleLifecycleService.autoCloseTimeoutPendingHomework(30);
            log.info("SampleLifecycleJob auto close completed, closed={}", closed);
        } catch (Exception ex) {
            log.error("SampleLifecycleJob auto close failed", ex);
        }
    }
}
