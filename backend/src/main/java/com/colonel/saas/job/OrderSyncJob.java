package com.colonel.saas.job;

import com.colonel.saas.service.OrderSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderSyncJob {

    private final OrderSyncService orderSyncService;
    @Value("${order.sync.enabled:true}")
    private boolean enabled = true;

    public OrderSyncJob(OrderSyncService orderSyncService) {
        this.orderSyncService = orderSyncService;
    }

    @Scheduled(cron = "0 */10 * * * ?")
    public void syncOrders() {
        if (!enabled) {
            log.info("OrderSyncJob skipped: order.sync.enabled=false");
            return;
        }
        try {
            OrderSyncService.SyncResult result = orderSyncService.syncLatestWindow();
            if (result.locked()) {
                log.info("OrderSyncJob skipped, another process is running");
                return;
            }
            log.info("OrderSyncJob done, window=[{}, {}], pages={}, inserted={}, skipped={}",
                    result.startTime(), result.endTime(), result.pages(), result.inserted(), result.skipped());
        } catch (Exception e) {
            log.error("OrderSyncJob failed", e);
            throw e;
        }
    }
}
