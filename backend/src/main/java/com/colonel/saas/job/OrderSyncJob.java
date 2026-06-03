package com.colonel.saas.job;

import com.colonel.saas.service.OrderSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单同步定时任务。
 * <p>
 * 默认每 10 分钟拉取 6468 事实订单和 2704 增量结算订单；同时每 30 分钟回扫近 6 小时窗口（PAY_RECENT），
 * 用于兜底"刚付款订单 update 事件延迟"导致的不可见。三条路径使用独立的分布式锁、
 * Redis 水位 key 与日志 mode 标签，互不覆盖。
 * </p>
 * <p>
 * 同步结果将触发 {@link com.colonel.saas.event.OrderSyncedEvent}，由下游监听器
 * 驱动业绩计算、寄样状态更新等流程。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>{@link #syncOrders()}：Cron {@code 0 0/10 * * * ?}，受
 *       {@code order.sync.enabled} 控制，默认启用。</li>
 *   <li>{@link #syncPayRecent()}：Cron {@code 0 0/30 * * * ?}，受
 *       {@code order.sync.pay-recent.enabled} 控制，默认启用；窗口由
 *       {@link OrderSyncService#syncPayRecentWindow()} 固定为 6 小时。</li>
 *   <li>锁机制由 {@link OrderSyncService} 内部管理，分别使用
 *       {@link JobLockKeys#ORDER_SYNC} 与 {@link JobLockKeys#ORDER_SYNC_PAY_RECENT}。</li>
 *   <li>异常会向上传播，由 Spring 调度框架记录。</li>
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
    /** 是否启用 PAY_RECENT 近窗口补拉，可通过 {@code order.sync.pay-recent.enabled=false} 关闭 */
    @Value("${order.sync.pay-recent.enabled:true}")
    private boolean payRecentEnabled = true;
    /** 是否启用 INSTITUTE_RECENT（6468）团长事实源同步，可通过 {@code order.sync.institute-recent.enabled=false} 关闭 */
    @Value("${order.sync.institute-recent.enabled:true}")
    private boolean instituteRecentEnabled = true;

    public OrderSyncJob(OrderSyncService orderSyncService) {
        this.orderSyncService = orderSyncService;
    }

    /**
     * 执行增量订单同步。
     * <p>
     * 拉取最近窗口期（通常为过去 10 分钟）的订单数据。
     * 若服务内部检测到已有进程持有锁，则跳过本次执行。
     * </p>
     */
    @Scheduled(cron = "${order.sync.cron:0 */10 * * * ?}")
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
            log.info("OrderSyncJob done, mode=INCREMENTAL, window=[{}, {}], pages={}, inserted={}, updated={}, attributed={}, unattributed={}, failed={}",
                    result.startTime(), result.endTime(), result.pages(),
                    result.created(), result.updated(), result.attributed(),
                    result.unattributed(), result.failed());
        } catch (Exception e) {
            log.error("OrderSyncJob failed", e);
            throw e;
        }
    }

    /**
     * 执行 PAY_RECENT 近窗口补拉。
     * <p>
     * 固定窗口 now-6h ~ now，与 {@link #syncOrders()} 使用<strong>独立的</strong>
     * 分布式锁与 Redis 水位 key，避免互相覆盖。用于兜底"刚付款订单 update 事件
     * 延迟"导致的不可见。
     * </p>
     */
    @Scheduled(cron = "${order.sync.pay-recent.cron:0 */30 * * * ?}")
    public void syncPayRecent() {
        if (!payRecentEnabled) {
            log.info("OrderSyncJob.syncPayRecent skipped: order.sync.pay-recent.enabled=false");
            return;
        }
        try {
            OrderSyncService.SyncResult result = orderSyncService.syncPayRecentWindow();
            if (result.locked()) {
                log.info("OrderSyncJob.syncPayRecent skipped, another process is running");
                return;
            }
            log.info("OrderSyncJob.syncPayRecent done, mode=PAY_RECENT, window=[{}, {}], pages={}, inserted={}, updated={}, attributed={}, unattributed={}, failed={}",
                    result.startTime(), result.endTime(), result.pages(),
                    result.created(), result.updated(), result.attributed(),
                    result.unattributed(), result.failed());
        } catch (Exception e) {
            log.error("OrderSyncJob.syncPayRecent failed", e);
            throw e;
        }
    }

    /**
     * 执行 INSTITUTE_RECENT（6468 / buyin.instituteOrderColonel）团长事实源同步。
     * <p>
     * 固定窗口 now-24h ~ now，与 {@link #syncOrders()} 和 {@link #syncPayRecent()} 使用
     * <strong>独立的</strong>分布式锁与 Redis 水位 key。6468 只写事实/预估轨，
     * 不写入结算轨字段。
     * </p>
     */
    @Scheduled(cron = "${order.sync.institute-recent.cron:0 */10 * * * ?}")
    public void syncInstituteOrdersRecent() {
        if (!instituteRecentEnabled) {
            log.info("OrderSyncJob.syncInstituteOrdersRecent skipped: order.sync.institute-recent.enabled=false");
            return;
        }
        try {
            OrderSyncService.SyncResult result = orderSyncService.syncInstituteOrdersRecentWindow();
            if (result.locked()) {
                log.info("OrderSyncJob.syncInstituteOrdersRecent skipped, another process is running");
                return;
            }
            log.info("OrderSyncJob.syncInstituteOrdersRecent done, mode=INSTITUTE_RECENT, window=[{}, {}], pages={}, inserted={}, updated={}, attributed={}, unattributed={}, failed={}",
                    result.startTime(), result.endTime(), result.pages(),
                    result.created(), result.updated(), result.attributed(),
                    result.unattributed(), result.failed());
        } catch (Exception e) {
            log.error("OrderSyncJob.syncInstituteOrdersRecent failed", e);
            throw e;
        }
    }
}
