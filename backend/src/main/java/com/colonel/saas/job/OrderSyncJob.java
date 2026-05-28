package com.colonel.saas.job;

import com.colonel.saas.service.OrderSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单同步定时任务。
 * <p>
 * 每 10 分钟执行一次，从抖音电商开放平台拉取最近窗口期内的订单数据并写入本地数据库。
 * 该任务是订单域数据源的核心入口，同步结果将触发 {@link com.colonel.saas.event.OrderSyncedEvent}，
 * 由下游监听器驱动业绩计算、寄样状态更新等流程。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：{@code 0 0/10 * * * ?}（每 10 分钟整点执行）</li>
 *   <li>通过 {@code order.sync.enabled} 配置项控制开关，默认启用</li>
 *   <li>锁机制由 {@link OrderSyncService#syncLatestWindow()} 内部管理（非本类 DistributedJobLockService）</li>
 *   <li>异常会向上传播，由 Spring 调度框架记录</li>
 * </ul>
 * </p>
 *
 * @see OrderSyncService
 * @see com.colonel.saas.event.OrderSyncedEvent
 */
@Slf4j
@Component
public class OrderSyncJob {

    /** 订单同步服务，负责与抖音 API 交互并持久化订单 */
    private final OrderSyncService orderSyncService;
    /** 是否启用订单同步任务，可通过 {@code order.sync.enabled=false} 关闭 */
    @Value("${order.sync.enabled:true}")
    private boolean enabled = true;

    public OrderSyncJob(OrderSyncService orderSyncService) {
        this.orderSyncService = orderSyncService;
    }

    /**
     * 执行订单同步。
     * <p>
     * 拉取最近窗口期（通常为过去 10 分钟）的订单数据。
     * 若服务内部检测到已有进程持有锁，则跳过本次执行。
     * </p>
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void syncOrders() {
        if (!enabled) {
            log.info("OrderSyncJob skipped: order.sync.enabled=false");
            return;
        }
        try {
            OrderSyncService.SyncResult result = orderSyncService.syncLatestWindow();
            // 检查是否被其他实例的锁跳过
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
