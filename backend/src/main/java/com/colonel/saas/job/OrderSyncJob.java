package com.colonel.saas.job;

import com.colonel.saas.domain.order.application.OrderSyncApplicationService;
import com.colonel.saas.domain.order.application.OrderSyncCommand;
import com.colonel.saas.domain.order.application.OrderSyncExecutionContext;
import com.colonel.saas.domain.order.application.OrderSyncResult;
import com.colonel.saas.config.RequiresCompatibleSchema;
import com.colonel.saas.service.OrderSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单同步定时任务。
 * <p>
 * 1603 拆为热同步（每分钟小窗口）、补偿同步（每 10 分钟水位滚动）与结算口径同步；
 * 增量按 {@code order.sync.cron} 调度；每 6 小时额外跑一轮 24h 全量回补。同时每 30 分钟
 * 回扫近 6 小时窗口（PAY_RECENT），用于兜底"刚付款订单 update 事件延迟"导致的不可见。
 * </p>
 * <p>
 * 同步结果将触发 {@link com.colonel.saas.event.OrderSyncedEvent}，由下游监听器
 * 驱动业绩计算、寄样状态更新等流程。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>{@link #syncOrders()}：Cron {@code 0 0/3 * * * ?}，受
 *       {@code order.sync.enabled} 控制，默认启用。</li>
 *   <li>{@link #syncInstituteOrdersHot()}：Cron {@code 0 0/1 * * * ?}，1603 近实时热同步。</li>
 *   <li>{@link #syncInstituteOrdersRecent()}：Cron {@code 0 0/10 * * * ?}，1603 补偿近窗。</li>
 *   <li>{@link #syncInstituteFullBackfill()}：Cron {@code 0 15 0/6 * * ?}，1603 24h 兜底。</li>
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
@RequiresCompatibleSchema
public class OrderSyncJob {

    /** 订单同步服务，负责与抖音 API 交互并持久化订单 */
    private final OrderSyncService orderSyncService;
    /** 订单同步应用层入口（DDD-ORDER-001，受 refactor 开关控制） */
    private final OrderSyncApplicationService orderSyncApplicationService;
    /** 是否启用订单同步任务，可通过 {@code order.sync.enabled=false} 关闭 */
    @Value("${order.sync.enabled:true}")
    private boolean enabled = true;
    /** 是否启用 PAY_RECENT 近窗口补拉，可通过 {@code order.sync.pay-recent.enabled=false} 关闭 */
    @Value("${order.sync.pay-recent.enabled:true}")
    private boolean payRecentEnabled = true;
    /** 是否启用 INSTITUTE_RECENT（1603）团长事实源同步，可通过 {@code order.sync.institute-recent.enabled=false} 关闭 */
    @Value("${order.sync.institute-recent.enabled:true}")
    private boolean instituteRecentEnabled = true;

    /** 是否启用 INSTITUTE_HOT_RECENT（1603 近实时热同步） */
    @Value("${order.sync.institute-hot.enabled:true}")
    private boolean instituteHotEnabled = true;

    /** 是否启用 INSTITUTE 24h 全量回补，可通过 {@code order.sync.institute-backfill.enabled=false} 关闭 */
    @Value("${order.sync.institute-backfill.enabled:true}")
    private boolean instituteBackfillEnabled = true;

    /** 是否启用 1603 结算时间轨（time_type=settle）独立回扫 */
    @Value("${order.sync.settle.enabled:true}")
    private boolean settleSyncEnabled = true;

    public OrderSyncJob(
            OrderSyncService orderSyncService,
            OrderSyncApplicationService orderSyncApplicationService) {
        this.orderSyncService = orderSyncService;
        this.orderSyncApplicationService = orderSyncApplicationService;
    }

    /**
     * 执行增量订单同步。
     * <p>
     * 拉取最近窗口期（通常为过去数分钟）的订单数据。
     * 若服务内部检测到已有进程持有锁，则跳过本次执行。
     * </p>
     */
    @Scheduled(cron = "${order.sync.cron:0 */3 * * * ?}")
    public void syncOrders() {
        if (!enabled) {
            log.info("OrderSyncJob skipped: order.sync.enabled=false");
            return;
        }
        try {
            OrderSyncService.SyncResult result = runScheduled(
                    OrderSyncCommand.scheduledIncremental(),
                    OrderSyncExecutionContext.scheduled(OrderSyncExecutionContext.TASK_INCREMENTAL),
                    orderSyncService::syncLatestWindow);
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
            OrderSyncService.SyncResult result = runScheduled(
                    OrderSyncCommand.scheduledPayRecent(),
                    OrderSyncExecutionContext.scheduled(OrderSyncExecutionContext.TASK_PAY_RECENT),
                    orderSyncService::syncPayRecentWindow);
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
     * 执行 INSTITUTE_HOT_RECENT（1603）近实时热同步：小窗口、限页数，目标 freshness ≤ 2min。
     */
    @Scheduled(cron = "${order.sync.institute-hot.cron:0 */1 * * * ?}")
    public void syncInstituteOrdersHot() {
        if (!instituteHotEnabled) {
            log.info("OrderSyncJob.syncInstituteOrdersHot skipped: order.sync.institute-hot.enabled=false");
            return;
        }
        try {
            OrderSyncService.SyncResult result = runScheduled(
                    OrderSyncCommand.scheduledIncremental(),
                    OrderSyncExecutionContext.scheduled(OrderSyncExecutionContext.TASK_INSTITUTE_HOT),
                    orderSyncService::syncInstituteOrdersHotRecent);
            if (result.locked()) {
                log.info("OrderSyncJob.syncInstituteOrdersHot skipped, another process is running");
                return;
            }
            log.info("OrderSyncJob done, mode=INSTITUTE_HOT_RECENT, window=[{}, {}], pages={}, inserted={}, updated={}, failed={}, stopReason={}",
                    result.startTime(), result.endTime(), result.pages(),
                    result.created(), result.updated(), result.failed(), result.stopReason());
        } catch (Exception e) {
            log.error("OrderSyncJob.syncInstituteOrdersHot failed", e);
            throw e;
        }
    }

    /**
     * 执行 INSTITUTE_RECENT（1603）增量同步：按 institute 水位滚动近窗。
     */
    @Scheduled(cron = "${order.sync.institute-recent.cron:0 */10 * * * ?}")
    public void syncInstituteOrdersRecent() {
        if (!instituteRecentEnabled) {
            log.info("OrderSyncJob.syncInstituteOrdersRecent skipped: order.sync.institute-recent.enabled=false");
            return;
        }
        try {
            OrderSyncService.SyncResult result = runScheduled(
                    OrderSyncCommand.scheduledIncremental(),
                    OrderSyncExecutionContext.scheduled(OrderSyncExecutionContext.TASK_INSTITUTE_RECENT),
                    orderSyncService::syncInstituteOrdersRecentWindow);
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

    /**
     * 执行 INSTITUTE 24h 全量回补（1603）。
     * <p>
     * 与增量任务共用 institute 锁；增量任务已改为水位滚动，本任务用于定时兜底漏单。
     * </p>
     */
    /**
     * 执行 1603 结算时间轨回扫（{@code time_type=settle}）。
     * <p>
     * 与 INCREMENTAL（update）独立锁与 Redis 水位；上游空窗时不推进 settle 水位。
     * </p>
     */
    @Scheduled(cron = "${order.sync.settle.cron:0 5 */6 * * ?}")
    public void syncSettlementSettle() {
        if (!settleSyncEnabled) {
            log.info("OrderSyncJob.syncSettlementSettle skipped: order.sync.settle.enabled=false");
            return;
        }
        try {
            OrderSyncService.SyncResult result = runScheduled(
                    OrderSyncCommand.scheduledSettle(),
                    OrderSyncExecutionContext.scheduled(OrderSyncExecutionContext.TASK_SETTLE),
                    orderSyncService::syncSettlementSettleWindow);
            if (result.locked()) {
                log.info("OrderSyncJob.syncSettlementSettle skipped, another process is running");
                return;
            }
            log.info("OrderSyncJob.syncSettlementSettle done, mode=SETTLE, timeType=settle, window=[{}, {}], "
                            + "pages={}, fetched={}, inserted={}, updated={}, failed={}, stopReason={}",
                    result.startTime(), result.endTime(), result.pages(), result.totalFetched(),
                    result.created(), result.updated(), result.failed(), result.stopReason());
        } catch (Exception e) {
            log.error("OrderSyncJob.syncSettlementSettle failed", e);
            throw e;
        }
    }

    @Scheduled(cron = "${order.sync.institute-backfill.cron:0 15 0/6 * * ?}")
    public void syncInstituteFullBackfill() {
        if (!instituteBackfillEnabled) {
            log.info("OrderSyncJob.syncInstituteFullBackfill skipped: order.sync.institute-backfill.enabled=false");
            return;
        }
        try {
            OrderSyncService.SyncResult result = runScheduled(
                    OrderSyncCommand.scheduledIncremental(),
                    OrderSyncExecutionContext.scheduled(OrderSyncExecutionContext.TASK_INSTITUTE_BACKFILL),
                    orderSyncService::syncInstituteFullBackfillWindow);
            if (result.locked()) {
                log.info("OrderSyncJob.syncInstituteFullBackfill skipped, another process is running");
                return;
            }
            log.info("OrderSyncJob.syncInstituteFullBackfill done, mode=INSTITUTE_RECENT_FULL, window=[{}, {}], pages={}, inserted={}, updated={}, attributed={}, unattributed={}, failed={}",
                    result.startTime(), result.endTime(), result.pages(),
                    result.created(), result.updated(), result.attributed(),
                    result.unattributed(), result.failed());
        } catch (Exception e) {
            log.error("OrderSyncJob.syncInstituteFullBackfill failed", e);
            throw e;
        }
    }

    /**
     * 默认定时任务走旧 {@link OrderSyncService}；开启 {@code ddd.refactor.order-application.enabled} 时走应用层。
     */
    private OrderSyncService.SyncResult runScheduled(
            OrderSyncCommand command,
            OrderSyncExecutionContext context,
            java.util.function.Supplier<OrderSyncService.SyncResult> legacyFallback) {
        if (orderSyncApplicationService.isRoutingEnabled()) {
            OrderSyncResult applicationResult = orderSyncApplicationService.execute(command, context);
            return applicationResult.toLegacySyncResult();
        }
        return legacyFallback.get();
    }
}
