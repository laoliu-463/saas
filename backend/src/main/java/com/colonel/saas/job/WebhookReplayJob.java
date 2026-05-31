package com.colonel.saas.job;

import com.colonel.saas.service.DouyinWebhookEventService;
import com.colonel.saas.service.DouyinWebhookEventService.ReplayResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebhookReplayJob {

    private final DouyinWebhookEventService webhookEventService;

    @Value("${douyin.webhook.replay.enabled:true}")
    private boolean enabled = true;

    public WebhookReplayJob(DouyinWebhookEventService webhookEventService) {
        this.webhookEventService = webhookEventService;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void replayUnfinished() {
        if (!enabled) {
            return;
        }
        try {
            ReplayResult result = webhookEventService.replayUnfinished(50);
            if (result.scanned() == 0) {
                return;
            }
            log.info("WebhookReplayJob done, scanned={}, consumed={}, failed={}",
                    result.scanned(), result.consumed(), result.failed());
        } catch (Exception e) {
            log.error("WebhookReplayJob failed", e);
        }
    }
}
