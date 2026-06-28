package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.AppProperties;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.application.OrderSyncService;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 订单同步应用层入口（DDD-ORDER-001）。
 * <p>当前委派 {@link OrderSyncService}，不改变同步窗口、分页、checkpoint、锁、重试与落库行为。</p>
 */
@Slf4j
@Service
public class OrderSyncApplicationService {

    private static final String LAST_SYNC_TIME_KEY = "order:sync:last_time";
    private static final String PAY_RECENT_LAST_SYNC_TIME_KEY = "order:sync:pay_recent_last_time";
    private static final String SETTLE_LAST_SYNC_TIME_KEY = "order:sync:settle_last_time";
    private static final String INSTITUTE_RECENT_LAST_SYNC_TIME_KEY = "order:sync:institute_recent_last_time";
    private static final String INSTITUTE_HOT_LAST_SYNC_TIME_KEY = "order:sync:institute_hot_last_time";

    private final OrderSyncService orderSyncService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;
    private final DddRefactorProperties dddRefactorProperties;

    public OrderSyncApplicationService(
            OrderSyncService orderSyncService,
            RedisTemplate<String, Object> redisTemplate,
            AppProperties appProperties,
            DddRefactorProperties dddRefactorProperties) {
        this.orderSyncService = orderSyncService;
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /**
     * 是否启用应用层路由（需同时打开根开关与 order-application 子开关）。
     */
    public boolean isRoutingEnabled() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getOrderApplication().isEnabled();
    }

    /**
     * 执行订单同步命令，委派旧 {@link OrderSyncService}。
     */
    public OrderSyncResult execute(OrderSyncCommand command, OrderSyncExecutionContext context) {
        long startedAtMs = System.currentTimeMillis();
        String checkpointKey = resolveCheckpointKey(command, context);
        long checkpointBefore = readCheckpoint(checkpointKey);

        if (command.dryRun() || command.mode() == OrderSyncMode.DRY_RUN) {
            return OrderSyncResult.dryRunSkipped(checkpointBefore, startedAtMs);
        }

        OrderSyncService.SyncResult legacy = delegateToLegacy(command, context);
        long checkpointAfter = readCheckpoint(checkpointKey);
        return OrderSyncResult.fromLegacy(
                legacy,
                checkpointBefore,
                checkpointAfter,
                System.currentTimeMillis() - startedAtMs);
    }

    private OrderSyncService.SyncResult delegateToLegacy(
            OrderSyncCommand command,
            OrderSyncExecutionContext context) {
        return switch (command.mode()) {
            case SCHEDULED -> delegateScheduled(context);
            case MANUAL -> orderSyncService.triggerManualSync();
            case HISTORICAL -> orderSyncService.syncByTimeRange(
                    requireEpoch(command.startTime(), "startTime"),
                    requireEpoch(command.endTime(), "endTime"));
            case DRY_RUN -> throw new IllegalStateException("dry-run should be handled before delegation");
        };
    }

    private OrderSyncService.SyncResult delegateScheduled(OrderSyncExecutionContext context) {
        String task = context == null ? null : context.scheduledTask();
        if (!StringUtils.hasText(task)) {
            return orderSyncService.syncLatestWindow();
        }
        return switch (task) {
            case OrderSyncExecutionContext.TASK_INCREMENTAL -> orderSyncService.syncLatestWindow();
            case OrderSyncExecutionContext.TASK_PAY_RECENT -> orderSyncService.syncPayRecentWindow();
            case OrderSyncExecutionContext.TASK_INSTITUTE_HOT -> orderSyncService.syncInstituteOrdersHotRecent();
            case OrderSyncExecutionContext.TASK_INSTITUTE_RECENT -> orderSyncService.syncInstituteOrdersRecentWindow();
            case OrderSyncExecutionContext.TASK_SETTLE -> orderSyncService.syncSettlementSettleWindow();
            case OrderSyncExecutionContext.TASK_INSTITUTE_BACKFILL -> orderSyncService.syncInstituteFullBackfillWindow();
            default -> orderSyncService.syncLatestWindow();
        };
    }

    private long requireEpoch(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException("Order sync command missing " + field);
        }
        return value;
    }

    String resolveCheckpointKey(OrderSyncCommand command, OrderSyncExecutionContext context) {
        if (command.mode() == OrderSyncMode.HISTORICAL || command.mode() == OrderSyncMode.MANUAL) {
            return LAST_SYNC_TIME_KEY;
        }
        if (context != null && StringUtils.hasText(context.scheduledTask())) {
            return switch (context.scheduledTask()) {
                case OrderSyncExecutionContext.TASK_PAY_RECENT -> PAY_RECENT_LAST_SYNC_TIME_KEY;
                case OrderSyncExecutionContext.TASK_SETTLE -> SETTLE_LAST_SYNC_TIME_KEY;
                case OrderSyncExecutionContext.TASK_INSTITUTE_HOT -> INSTITUTE_HOT_LAST_SYNC_TIME_KEY;
                case OrderSyncExecutionContext.TASK_INSTITUTE_RECENT,
                     OrderSyncExecutionContext.TASK_INSTITUTE_BACKFILL -> INSTITUTE_RECENT_LAST_SYNC_TIME_KEY;
                default -> LAST_SYNC_TIME_KEY;
            };
        }
        if (command.timeType() == OrderSyncTimeType.SETTLE) {
            return SETTLE_LAST_SYNC_TIME_KEY;
        }
        return LAST_SYNC_TIME_KEY;
    }

    long readCheckpoint(String key) {
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            return asLong(raw, 0L);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (appProperties.getTest().isEnabled()) {
                log.warn("Redis unavailable in test mode when reading checkpoint {}, fallback to 0: {}",
                        key, ex.getMessage());
                return 0L;
            }
            throw ex;
        }
    }

    private long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
