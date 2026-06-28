package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.AppProperties;
import com.colonel.saas.domain.order.application.OrderAmountMappingRouter;
import com.colonel.saas.domain.order.application.OrderAmountMappingRouter.SyncSource;
import com.colonel.saas.domain.order.application.OrderAttributionRouter;
import com.colonel.saas.domain.order.infrastructure.OrderSyncPersistenceService;
import com.colonel.saas.job.JobLockKeys;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.time.AppZone;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.AttributionSourceNormalizer;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.settlement.InstituteOrderColonelSettlementGateway;
import com.colonel.saas.service.settlement.MultiSettlementOrderFallbackGateway;
import com.colonel.saas.service.settlement.SettlementOrderGateway;
import com.colonel.saas.service.settlement.SettlementOrderPage;
import com.colonel.saas.service.settlement.SettlementOrderQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 订单同步服务：从抖音结算网关拉取最新订单并持久化。
 * <p>
 * 支持按时间窗口自动同步、手动触发、按订单号精确同步三种模式；
 * 内置分布式锁（Redis）防并发，熔断器保护上游网关；同步过程中
 * 通过 {@link OrderAttributionRouter} 完成订单归属解析与字段写入。
 */
@Slf4j
@Service
public class OrderSyncService {

    /** Redis key 存储上次同步的截止时间（epoch seconds）。 */
    private static final String LAST_SYNC_TIME_KEY = "order:sync:last_time";
    /** Redis key 存储 PAY_RECENT 近窗口补拉的上次同步截止时间（与 {@link #LAST_SYNC_TIME_KEY} 完全独立）。 */
    private static final String PAY_RECENT_LAST_SYNC_TIME_KEY = "order:sync:pay_recent_last_time";
    /** Redis key 存储 SETTLE（2704 time_type=settle）的上次同步截止时间。 */
    private static final String SETTLE_LAST_SYNC_TIME_KEY = "order:sync:settle_last_time";
    /** Redis key 存储 INSTITUTE_RECENT（6468 团长事实源）的上次同步截止时间。 */
    private static final String INSTITUTE_RECENT_LAST_SYNC_TIME_KEY = "order:sync:institute_recent_last_time";
    /** Redis key 存储 INSTITUTE_HOT_RECENT（6468 近实时热同步）的上次成功截止时间。 */
    private static final String INSTITUTE_HOT_LAST_SYNC_TIME_KEY = "order:sync:institute_hot_last_time";
    /** 分布式同步锁 TTL，防止同步任务长时间占用。 */
    private static final Duration SYNC_LOCK_TTL = Duration.ofMinutes(10);
    /** PAY_RECENT 近窗口补拉的回扫长度（秒），约 6 小时；用于兜底"刚付款订单 update 延迟"。 */
    private static final long PAY_RECENT_WINDOW_SECONDS = 6L * 60L * 60L;
    /** INSTITUTE 全量回补窗口（秒），约 24 小时；仅首次或定时全量任务使用。 */
    private static final long INSTITUTE_FULL_BACKFILL_WINDOW_SECONDS = 24L * 60L * 60L;
    /** 每页请求默认拉取数量。 */
    private static final int DEFAULT_COUNT = 100;
    /** 默认最大翻页次数，超过后强制停止，防止无限循环。 */
    private static final int DEFAULT_MAX_PAGES = 500;
    /** 默认最大订单处理行数，防止一次同步过量拉取。 */
    private static final int DEFAULT_MAX_ORDERS = 50_000;
    private static final String STOP_REASON_LOCKED = "LOCKED";
    private static final String STOP_REASON_EMPTY_PAGE = "EMPTY_PAGE";
    private static final String STOP_REASON_NO_NEXT_CURSOR = "NO_NEXT_CURSOR";
    private static final String STOP_REASON_DUPLICATE_CURSOR = "DUPLICATE_CURSOR";
    private static final String STOP_REASON_MAX_PAGES = "MAX_PAGES";
    private static final String STOP_REASON_MAX_ORDERS = "MAX_ORDERS";
    private static final String STOP_REASON_SINGLE_PAGE = "SINGLE_PAGE";
    private static final String STOP_REASON_FETCH_ERROR = "FETCH_ERROR";
    private static final String STOP_REASON_UNKNOWN = "UNKNOWN";

    /** 同步模式标签：默认 10 分钟增量窗口（依赖 last_time 滚动）。 */
    static final String SYNC_MODE_INCREMENTAL = "INCREMENTAL";
    /** 同步模式标签：6 小时近窗口补拉（保留独立 last_time，避免覆盖增量水位）。 */
    static final String SYNC_MODE_PAY_RECENT = "PAY_RECENT";
    /** 同步模式标签：手动按订单号精确同步。 */
    static final String SYNC_MODE_SPECIFIC = "SPECIFIC";
    /** 同步模式标签：团长事实源（6468 / buyin.instituteOrderColonel），主订单事实 + 预估轨 + 已结算普通单结算轨。 */
    static final String SYNC_MODE_INSTITUTE_RECENT = "INSTITUTE_RECENT";
    /** 同步模式标签：6468 近实时热同步（小窗口、限页数，与补偿任务分锁）。 */
    static final String SYNC_MODE_INSTITUTE_HOT_RECENT = "INSTITUTE_HOT_RECENT";
    /** 同步模式标签：2704 分次结算补充源（time_type=settle）独立回扫；fetched=0 不代表无订单。 */
    static final String SYNC_MODE_SETTLE = "SETTLE";
    private static final String API_INSTITUTE_ORDER_COLONEL = "buyin.instituteOrderColonel";
    private static final String API_COLONEL_MULTI_SETTLEMENT_ORDERS = "buyin.colonelMultiSettlementOrders";

    /**
     * 抖音 colonelMultiSettlementOrders 的 time_type 取值，仅支持 settle / update。
     * INCREMENTAL / PAY_RECENT 使用 update；独立 SETTLE 任务使用 settle。
     * 抖音不存在 time_type=pay。
     */
    static final String GATEWAY_TIME_TYPE_UPDATE = "update";
    static final String GATEWAY_TIME_TYPE_SETTLE = "settle";

    private static final DateTimeFormatter RAW_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DouyinOrderGateway douyinOrderGateway;
    private final SettlementOrderGateway instituteSettlementGateway;
    private final SettlementOrderGateway multiSettlementFallbackGateway;
    private final OrderSyncPersistenceService persistenceService;
    private final OrderAttributionRouter orderAttributionRouter;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedJobLockService jobLockService;
    private final AppProperties appProperties;
    private final OrderAmountMappingRouter orderAmountMappingRouter;
    private volatile long localLastSyncTime;
    private final AtomicInteger consecutiveGatewayFailures = new AtomicInteger();
    private volatile Instant gatewayCircuitOpenedAt;

    @Value("${order.sync.circuit-breaker.failure-threshold:3}")
    private int gatewayFailureThreshold = 3;

    @Value("${order.sync.circuit-breaker.open-duration:PT5M}")
    private Duration gatewayCircuitOpenDuration = Duration.ofMinutes(5);

    @Value("${order.sync.max-pages:500}")
    private int maxPages = DEFAULT_MAX_PAGES;

    @Value("${order.sync.max-orders:50000}")
    private int maxOrders = DEFAULT_MAX_ORDERS;

    /** 相对当前时间的滞后（秒），避免查询未稳定的上游数据。 */
    @Value("${order.sync.lag-seconds:60}")
    private long lagSeconds = 60L;

    /** 增量同步默认窗口长度（秒）。 */
    @Value("${order.sync.window-seconds:600}")
    private long windowSeconds = 600L;

    /** 增量窗口与前次水位重叠（秒），补偿边界订单。 */
    @Value("${order.sync.overlap-seconds:60}")
    private long overlapSeconds = 60L;

    @Value("${order.sync.institute-hot.lag-seconds:30}")
    private long instituteHotLagSeconds = 30L;

    @Value("${order.sync.institute-hot.window-seconds:300}")
    private long instituteHotWindowSeconds = 300L;

    @Value("${order.sync.institute-hot.overlap-seconds:120}")
    private long instituteHotOverlapSeconds = 120L;

    @Value("${order.sync.institute-hot.page-size:100}")
    private int instituteHotPageSize = DEFAULT_COUNT;

    @Value("${order.sync.institute-hot.max-pages:10}")
    private int instituteHotMaxPages = 10;

    @Value("${order.sync.institute-hot.max-orders:1000}")
    private int instituteHotMaxOrders = 1000;

    @Value("${order.sync.institute-hot.lock-ttl-seconds:90}")
    private long instituteHotLockTtlSeconds = 90L;

    @Value("${order.sync.settle.enabled:true}")
    private boolean settleSyncEnabled = true;

    @Value("${order.sync.settle.lag-seconds:60}")
    private long settleLagSeconds = 60L;

    @Value("${order.sync.settle.window-seconds:86400}")
    private long settleWindowSeconds = 86400L;

    @Value("${order.sync.settle.overlap-seconds:300}")
    private long settleOverlapSeconds = 300L;

    @Value("${douyin.order.settlement-source:instituteOrderColonel}")
    private String settlementSource = "instituteOrderColonel";

    @Value("${douyin.order.institute-settlement-time-type:update}")
    private String instituteSettlementTimeType = GATEWAY_TIME_TYPE_UPDATE;

    @Value("${douyin.order.settlement-fallback-enabled:false}")
    private boolean settlementFallbackEnabled = false;

    @Value("${douyin.order.settlement-dual-read-no-write-enabled:false}")
    private boolean settlementDualReadNoWriteEnabled = false;

    @Value("${douyin.order.allow-estimate-as-effective-fallback:false}")
    private boolean allowEstimateAsEffectiveFallback = false;

    public OrderSyncService(
            DouyinOrderGateway douyinOrderGateway,
            @Qualifier("instituteOrderColonelSettlementGateway") SettlementOrderGateway instituteSettlementGateway,
            @Qualifier("multiSettlementOrderFallbackGateway") SettlementOrderGateway multiSettlementFallbackGateway,
            OrderSyncPersistenceService persistenceService,
            OrderAttributionRouter orderAttributionRouter,
            RedisTemplate<String, Object> redisTemplate,
            DistributedJobLockService jobLockService,
            AppProperties appProperties,
            OrderAmountMappingRouter orderAmountMappingRouter) {
        this.douyinOrderGateway = douyinOrderGateway;
        this.instituteSettlementGateway = instituteSettlementGateway;
        this.multiSettlementFallbackGateway = multiSettlementFallbackGateway;
        this.persistenceService = persistenceService;
        this.orderAttributionRouter = orderAttributionRouter;
        this.redisTemplate = redisTemplate;
        this.jobLockService = jobLockService;
        this.appProperties = appProperties;
        this.orderAmountMappingRouter = orderAmountMappingRouter;
    }

    /** 判断当前是否处于 test 模式（影响 Redis 不可用时的降级策略）。 */
    private boolean testEnabled() {
        return appProperties.getTest().isEnabled();
    }

    /**
     * 自动同步最近时间窗口的订单。
     * <p>
     * 基于 Redis 中记录的上次同步截止时间计算窗口起点，
     * 首次运行时使用默认窗口大小；窗口向前扩展 OVERLAP_SECONDS 以补偿边界。
     */
    public SyncResult syncLatestWindow() {
        long now = Instant.now().getEpochSecond();
        long endTime = now - lagSeconds;
        long defaultStart = endTime - windowSeconds - overlapSeconds;
        long startTime = defaultStart;
        long lastSyncTime = readLastSyncTime();
        if (lastSyncTime > 0L) {
            startTime = Math.max(0L, lastSyncTime - overlapSeconds);
        }
        if (startTime >= endTime) {
            startTime = defaultStart;
        }
        return syncByTimeRange(startTime, endTime);
    }

    /** 手动触发同步（委托到 syncLatestWindow，语义入口）。 */
    public SyncResult triggerManualSync() {
        return syncLatestWindow();
    }

    /**
     * PAY_RECENT 近窗口补拉。
     * <p>
     * 用于兜底"刚付款订单 update 延迟"导致的不可见。固定窗口 now-6h ~ now，
     * 使用 {@link JobLockKeys#ORDER_SYNC_PAY_RECENT} 独立锁、{@link #PAY_RECENT_LAST_SYNC_TIME_KEY}
     * 独立 Redis key，<strong>不覆盖 {@link #LAST_SYNC_TIME_KEY}</strong>，与 10 分钟增量同步互不影响。
     * <p>
     * 上游 time_type 与增量同步一致使用 {@value #GATEWAY_TIME_TYPE_UPDATE}，因为抖音
     * colonelMultiSettlementOrders 不存在 time_type=pay；update 必然在 PAY_SUCC 之后命中，
     * 语义等效。
     *
     * @return 同步结果；锁冲突时返回 {@code locked=true}
     */
    public SyncResult syncPayRecentWindow() {
        long now = Instant.now().getEpochSecond();
        long endTime = now - lagSeconds;
        long startTime = Math.max(0L, endTime - PAY_RECENT_WINDOW_SECONDS);
        if (!jobLockService.tryAcquireStrict(JobLockKeys.ORDER_SYNC_PAY_RECENT, SYNC_LOCK_TTL)) {
            log.info("Order sync skipped, mode={}, timeType={}, reason=locked",
                    SYNC_MODE_PAY_RECENT, GATEWAY_TIME_TYPE_UPDATE);
            return new SyncResult(startTime, endTime, 0, 0, 0, true);
        }
        try {
            SyncResult result = syncRangeWithMode(
                    startTime, endTime, DEFAULT_COUNT, SYNC_MODE_PAY_RECENT, GATEWAY_TIME_TYPE_UPDATE);
            persistPayRecentLastSyncTime(endTime);
            return result;
        } finally {
            jobLockService.release(JobLockKeys.ORDER_SYNC_PAY_RECENT);
        }
    }

    /**
     * 2704 分次结算补充回扫（{@code buyin.colonelMultiSettlementOrders}，{@code time_type=settle}）。
     * <p>
     * 2704 补充分次结算明细与普通订单结算轨，不是主订单入库源，也不是结算轨唯一来源。
     * 使用独立锁 {@link JobLockKeys#ORDER_SYNC_SETTLE} 与 Redis 水位
     * {@link #SETTLE_LAST_SYNC_TIME_KEY}。上游整窗空结果（fetched=0）时不推进水位、不冲正订单，
     * 并记录 {@code upstream_empty} 证据日志。
     */
    public SyncResult syncSettlementSettleWindow() {
        long now = Instant.now().getEpochSecond();
        long endTime = now - settleLagSeconds;
        long startTime = resolveSettleStartTime(endTime);
        if (!jobLockService.tryAcquireStrict(JobLockKeys.ORDER_SYNC_SETTLE, SYNC_LOCK_TTL)) {
            log.info("Order sync skipped, mode={}, timeType={}, reason=locked",
                    SYNC_MODE_SETTLE, GATEWAY_TIME_TYPE_SETTLE);
            return new SyncResult(startTime, endTime, 0, 0, 0, true);
        }
        try {
            SyncResult result = syncRangeWithMode(
                    startTime, endTime, DEFAULT_COUNT, SYNC_MODE_SETTLE, GATEWAY_TIME_TYPE_SETTLE);
            if (shouldAdvanceSettleCheckpoint(result)) {
                persistSettleLastSyncTime(endTime);
            } else {
                log.warn("ORDER_SYNC_SETTLEMENT upstream_empty=true mode={} timeType={} range=[{}, {}] "
                                + "pagesFetched={} fetched={} stopReason={} checkpoint=not_advanced",
                        SYNC_MODE_SETTLE, GATEWAY_TIME_TYPE_SETTLE, startTime, endTime,
                        result.pages(), result.totalFetched(), result.stopReason());
            }
            return result;
        } finally {
            jobLockService.release(JobLockKeys.ORDER_SYNC_SETTLE);
        }
    }

    long resolveSettleStartTime(long endTime) {
        long lastSyncTime = readSettleLastSyncTime();
        if (lastSyncTime <= 0L) {
            return Math.max(0L, endTime - settleWindowSeconds);
        }
        long startTime = Math.max(0L, lastSyncTime - settleOverlapSeconds);
        if (startTime >= endTime) {
            startTime = Math.max(0L, endTime - settleWindowSeconds);
        }
        long maxWindow = 90L * 24L * 60L * 60L;
        if (endTime - startTime > maxWindow) {
            startTime = endTime - maxWindow;
        }
        return startTime;
    }

    boolean shouldAdvanceSettleCheckpoint(SyncResult result) {
        if (result == null || result.locked()) {
            return false;
        }
        return result.totalFetched() > 0 || result.uniqueOrders() > 0;
    }

    /**
     * INSTITUTE_HOT_RECENT（6468）近实时热同步：每分钟小窗口追最新付款单，限页数防堆积。
     * <p>
     * 窗口：{@code max(lastHot - overlap, now - window)} ~ {@code now - hotLag}；
     * 使用独立锁与 Redis 水位，失败不推进 checkpoint。
     */
    public SyncResult syncInstituteOrdersHotRecent() {
        long snapshotAt = Instant.now().getEpochSecond();
        long endTime = snapshotAt - instituteHotLagSeconds;
        long startTime = resolveInstituteHotStartTime(endTime);
        Long latestBefore = persistenceService.findLatestPayTimeEpochSeconds().orElse(null);
        Duration lockTtl = Duration.ofSeconds(Math.max(30L, instituteHotLockTtlSeconds));
        if (!jobLockService.tryAcquireStrict(JobLockKeys.ORDER_SYNC_INSTITUTE_HOT, lockTtl)) {
            log.info("task={} snapshotAt={} startTime={} endTime={} reason=locked",
                    SYNC_MODE_INSTITUTE_HOT_RECENT, snapshotAt, startTime, endTime);
            return new SyncResult(startTime, endTime, 0, 0, 0, true);
        }
        try {
            DouyinOrderGateway.OrderListResult firstPage = fetchInstitute(
                    new DouyinOrderGateway.DouyinOrderQueryRequest(startTime, endTime, instituteHotPageSize, "0"));
            SyncResult result = syncItemsWithLimits(
                    firstPage,
                    startTime,
                    endTime,
                    true,
                    instituteHotPageSize,
                    SYNC_MODE_INSTITUTE_HOT_RECENT,
                    API_INSTITUTE_ORDER_COLONEL,
                    SyncSource.INSTITUTE,
                    cursor -> fetchInstitute(new DouyinOrderGateway.DouyinOrderQueryRequest(
                            startTime, endTime, instituteHotPageSize, cursor)),
                    instituteHotMaxPages,
                    instituteHotMaxOrders);
            persistInstituteHotLastSyncTime(endTime);
            Long latestAfter = persistenceService.findLatestPayTimeEpochSeconds().orElse(null);
            logInstituteHotFreshness(snapshotAt, startTime, endTime, result, latestBefore, latestAfter);
            if (STOP_REASON_MAX_PAGES.equals(result.stopReason())) {
                log.warn("task={} stopReason=MAX_PAGES window=[{}, {}] uniqueOrders={} — defer to INSTITUTE_RECENT compensation",
                        SYNC_MODE_INSTITUTE_HOT_RECENT, startTime, endTime, result.uniqueOrders());
            }
            return result;
        } finally {
            jobLockService.release(JobLockKeys.ORDER_SYNC_INSTITUTE_HOT);
        }
    }

    /**
     * INSTITUTE_RECENT（6468）增量同步：按 institute 独立水位滚动近窗，避免每轮全量 24h 扫描。
     */
    public SyncResult syncInstituteOrdersRecentWindow() {
        long now = Instant.now().getEpochSecond();
        long endTime = now - lagSeconds;
        long startTime = resolveInstituteStartTime(endTime, false);
        return syncInstituteRange(startTime, endTime, SYNC_MODE_INSTITUTE_RECENT);
    }

    /**
     * INSTITUTE 全量回补：固定 now-24h ~ now，用于首次或定时兜底，与增量任务共用 institute 锁。
     */
    public SyncResult syncInstituteFullBackfillWindow() {
        long now = Instant.now().getEpochSecond();
        long endTime = now - lagSeconds;
        long startTime = resolveInstituteStartTime(endTime, true);
        return syncInstituteRange(startTime, endTime, SYNC_MODE_INSTITUTE_RECENT + "_FULL");
    }

    /**
     * 计算 6468 同步起点：增量模式跟 institute 水位；全量/首次回退 24h。
     */
    long resolveInstituteStartTime(long endTime, boolean fullBackfill) {
        if (fullBackfill) {
            return Math.max(0L, endTime - INSTITUTE_FULL_BACKFILL_WINDOW_SECONDS);
        }
        long lastSyncTime = readInstituteLastSyncTime();
        if (lastSyncTime <= 0L) {
            return Math.max(0L, endTime - INSTITUTE_FULL_BACKFILL_WINDOW_SECONDS);
        }
        long defaultStart = endTime - windowSeconds - overlapSeconds;
        long startTime = Math.max(0L, lastSyncTime - overlapSeconds);
        if (startTime >= endTime) {
            startTime = defaultStart;
        }
        if (endTime - startTime > INSTITUTE_FULL_BACKFILL_WINDOW_SECONDS) {
            startTime = endTime - INSTITUTE_FULL_BACKFILL_WINDOW_SECONDS;
        }
        return startTime;
    }

    /**
     * 热同步起点：有 hot 水位时 {@code lastHot - overlap}，否则 {@code now - window}。
     */
    long resolveInstituteHotStartTime(long endTime) {
        long windowStart = Math.max(0L, endTime - instituteHotWindowSeconds);
        long lastHot = readInstituteHotLastSyncTime();
        if (lastHot <= 0L) {
            return windowStart;
        }
        return Math.max(windowStart, lastHot - instituteHotOverlapSeconds);
    }

    private void logInstituteHotFreshness(
            long snapshotAt,
            long startTime,
            long endTime,
            SyncResult result,
            Long latestBefore,
            Long latestAfter) {
        Long freshnessLag = latestAfter == null ? null : Math.max(0L, snapshotAt - latestAfter);
        log.info("task={} snapshotAt={} startTime={} endTime={} pagesFetched={} uniqueOrders={} inserted={} updated={} failed={} latestPayTimeBefore={} latestPayTimeAfter={} freshnessLagSeconds={} stopReason={}",
                SYNC_MODE_INSTITUTE_HOT_RECENT,
                snapshotAt,
                startTime,
                endTime,
                result.pages(),
                result.uniqueOrders(),
                result.created(),
                result.updated(),
                result.failed(),
                latestBefore,
                latestAfter,
                freshnessLag,
                result.stopReason());
    }

    private SyncResult syncInstituteRange(long startTime, long endTime, String mode) {
        if (!jobLockService.tryAcquireStrict(JobLockKeys.ORDER_SYNC_INSTITUTE, SYNC_LOCK_TTL)) {
            log.info("ORDER_SYNC_INSTITUTE api={} mode={} reason=locked",
                    API_INSTITUTE_ORDER_COLONEL, mode);
            return new SyncResult(startTime, endTime, 0, 0, 0, true);
        }
        try {
            DouyinOrderGateway.OrderListResult firstPage = fetchInstitute(
                    new DouyinOrderGateway.DouyinOrderQueryRequest(startTime, endTime, DEFAULT_COUNT, "0"));
            SyncResult result = syncItemsWithLimits(
                    firstPage,
                    startTime,
                    endTime,
                    true,
                    DEFAULT_COUNT,
                    mode,
                    API_INSTITUTE_ORDER_COLONEL,
                    SyncSource.INSTITUTE,
                    cursor -> fetchInstitute(new DouyinOrderGateway.DouyinOrderQueryRequest(startTime, endTime, DEFAULT_COUNT, cursor)),
                    maxPages,
                    maxOrders);
            persistInstituteLastSyncTime(endTime);
            return result;
        } finally {
            jobLockService.release(JobLockKeys.ORDER_SYNC_INSTITUTE);
        }
    }

    /** 按时间范围调用抖音团长事实源接口 6468（受熔断器保护）。 */
    private DouyinOrderGateway.OrderListResult fetchInstitute(DouyinOrderGateway.DouyinOrderQueryRequest request) {
        return executeGatewayCall(() -> douyinOrderGateway.listInstituteOrders(request));
    }

    /** 读取 INSTITUTE 独立 Redis key 的上次同步时间；test 模式下 Redis 不可用时返回 0。 */
    long readInstituteLastSyncTime() {
        try {
            Object raw = redisTemplate.opsForValue().get(INSTITUTE_RECENT_LAST_SYNC_TIME_KEY);
            return asLong(raw, 0L);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled()) {
                log.warn("Redis unavailable in test mode when reading institute last sync time, fallback to 0: {}", ex.getMessage());
                return 0L;
            }
            throw ex;
        }
    }

    /** 写入 INSTITUTE 独立 Redis key 的上次同步时间；test 模式下 Redis 不可用时静默跳过。 */
    private void persistInstituteLastSyncTime(long endTime) {
        try {
            redisTemplate.opsForValue().set(INSTITUTE_RECENT_LAST_SYNC_TIME_KEY, String.valueOf(endTime));
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled()) {
                log.warn("Redis unavailable in test mode when persisting institute last sync time, skip: {}", ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    /** 读取 INSTITUTE_HOT 独立 Redis key 的上次成功截止时间。 */
    long readInstituteHotLastSyncTime() {
        try {
            Object raw = redisTemplate.opsForValue().get(INSTITUTE_HOT_LAST_SYNC_TIME_KEY);
            return asLong(raw, 0L);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled()) {
                log.warn("Redis unavailable in test mode when reading institute hot last sync time, fallback to 0: {}", ex.getMessage());
                return 0L;
            }
            throw ex;
        }
    }

    /** 热同步成功完成后写入 INSTITUTE_HOT 水位；失败路径不得调用。 */
    private void persistInstituteHotLastSyncTime(long endTime) {
        try {
            redisTemplate.opsForValue().set(INSTITUTE_HOT_LAST_SYNC_TIME_KEY, String.valueOf(endTime));
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled()) {
                log.warn("Redis unavailable in test mode when persisting institute hot last sync time, skip: {}", ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    /** 读取 PAY_RECENT 独立 Redis key 的上次同步时间；test 模式下 Redis 不可用时返回 0。 */
    long readPayRecentLastSyncTime() {
        try {
            Object raw = redisTemplate.opsForValue().get(PAY_RECENT_LAST_SYNC_TIME_KEY);
            return asLong(raw, 0L);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled()) {
                log.warn("Redis unavailable in test mode when reading pay_recent last sync time, fallback to 0: {}", ex.getMessage());
                return 0L;
            }
            throw ex;
        }
    }

    /** 读取 SETTLE 独立 Redis key 的上次同步时间。 */
    long readSettleLastSyncTime() {
        try {
            Object raw = redisTemplate.opsForValue().get(SETTLE_LAST_SYNC_TIME_KEY);
            return asLong(raw, 0L);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled()) {
                log.warn("Redis unavailable in test mode when reading settle last sync time, fallback to 0: {}",
                        ex.getMessage());
                return 0L;
            }
            throw ex;
        }
    }

    /** 写入 SETTLE 独立 Redis key 的上次同步时间。 */
    private void persistSettleLastSyncTime(long endTime) {
        try {
            redisTemplate.opsForValue().set(SETTLE_LAST_SYNC_TIME_KEY, String.valueOf(endTime));
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled()) {
                log.warn("Redis unavailable in test mode when persisting settle last sync time, skip: {}",
                        ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    /** 写入 PAY_RECENT 独立 Redis key 的上次同步时间；test 模式下 Redis 不可用时静默跳过。 */
    private void persistPayRecentLastSyncTime(long endTime) {
        try {
            redisTemplate.opsForValue().set(PAY_RECENT_LAST_SYNC_TIME_KEY, String.valueOf(endTime));
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled()) {
                log.warn("Redis unavailable in test mode when persisting pay_recent last sync time, skip: {}", ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    /**
     * 按指定订单号列表精确同步。
     * <p>
     * 先去重并校验，获取分布式锁后调用网关精确查询接口；
     * 锁冲突时抛出 CONFLICT 异常。
     *
     * @param orderIds 待同步的订单号列表（非空）
     * @return 同步结果
     * @throws BusinessException 参数为空时抛 PARAM，锁冲突时抛 CONFLICT
     */
    public SyncResult syncByOrderIds(List<String> orderIds) {
        List<String> normalizedOrderIds = normalizeOrderIds(orderIds);
        if (normalizedOrderIds.isEmpty()) {
            throw BusinessException.param("orderIds is required");
        }
        if (!acquireSyncLock()) {
            throw BusinessException.conflict("Order sync is busy");
        }
        try {
            return syncSpecificOrders(normalizedOrderIds);
        } finally {
            releaseSyncLock();
        }
    }

    /** 获取上次同步的截止时间，转为应用时区 LocalDateTime；未同步过时返回 null。 */
    public LocalDateTime getLastSyncTime() {
        long epochSecond = readLastSyncTime();
        if (epochSecond <= 0L) {
            return null;
        }
        return AppZone.fromEpochSecond(epochSecond);
    }

    /**
     * 按指定时间范围同步订单。
     * <p>
     * 获取分布式锁后执行同步；锁冲突时返回 locked=true 的空结果。
     * 成功后将 endTime 持久化为下次同步起点。
     *
     * @param startTime 开始时间（epoch seconds，非零）
     * @param endTime   结束时间（epoch seconds，大于 startTime）
     * @return 同步结果
     * @throws BusinessException 参数非法时抛 PARAM
     */
    public SyncResult syncByTimeRange(long startTime, long endTime) {
        if (startTime <= 0 || endTime <= 0 || startTime >= endTime) {
            throw BusinessException.param("Invalid sync time range");
        }
        if (!acquireSyncLock()) {
            return new SyncResult(startTime, endTime, 0, 0, 0, true);
        }
        try {
            SyncResult result = syncRangeWithMode(
                    startTime, endTime, DEFAULT_COUNT, SYNC_MODE_INCREMENTAL, GATEWAY_TIME_TYPE_UPDATE);
            persistLastSyncTime(endTime);
            return result;
        } finally {
            releaseSyncLock();
        }
    }

    /** 从 Redis 读取上次同步时间戳，test 模式下 Redis 不可用时回退到本地内存值。 */
    private long readLastSyncTime() {
        try {
            Object raw = redisTemplate.opsForValue().get(LAST_SYNC_TIME_KEY);
            return asLong(raw, localLastSyncTime);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled()) {
                log.warn("Redis unavailable in test mode when reading last sync time, fallback to local state: {}", ex.getMessage());
                return localLastSyncTime;
            }
            throw ex;
        }
    }

    /** 获取订单同步分布式锁（严格模式）。 */
    private boolean acquireSyncLock() {
        return jobLockService.tryAcquireStrict(JobLockKeys.ORDER_SYNC, SYNC_LOCK_TTL);
    }

    /** 将同步截止时间写入 Redis（同时更新本地缓存），test 模式下 Redis 不可用时仅保留本地值。 */
    private void persistLastSyncTime(long endTime) {
        localLastSyncTime = endTime;
        try {
            redisTemplate.opsForValue().set(LAST_SYNC_TIME_KEY, String.valueOf(endTime));
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled()) {
                log.warn("Redis unavailable in test mode when persisting last sync time, keep local state only: {}", ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    /** 释放订单同步分布式锁。 */
    private void releaseSyncLock() {
        jobLockService.release(JobLockKeys.ORDER_SYNC);
    }

    /**
     * 按时间窗口同步，附带 {@code mode} 标签用于日志和监控。
     * mode 取值：{@link #SYNC_MODE_INCREMENTAL} / {@link #SYNC_MODE_PAY_RECENT} / {@link #SYNC_MODE_SPECIFIC}。
     */
    private SyncResult syncRangeWithMode(long startTime, long endTime, int count, String mode, String timeType) {
        String resolvedTimeType = resolveSettlementTimeType(timeType);
        DouyinOrderGateway.DouyinOrderQueryRequest firstRequest =
                new DouyinOrderGateway.DouyinOrderQueryRequest(startTime, endTime, count, "0", resolvedTimeType);
        return syncItemsWithLimits(
                fetchSettlement(firstRequest),
                startTime,
                endTime,
                true,
                count,
                mode,
                resolvedTimeType,
                activeSettlementSyncSource(),
                cursor -> fetchSettlement(
                        new DouyinOrderGateway.DouyinOrderQueryRequest(startTime, endTime, count, cursor, resolvedTimeType)),
                maxPages,
                maxOrders);
    }

    /** 按订单号精确同步：调用网关精确查询接口，不翻页。 */
    private SyncResult syncSpecificOrders(List<String> orderIds) {
        long now = Instant.now().getEpochSecond();
        return syncItemsWithLimits(
                fetchSettlementByOrderIds(orderIds),
                now,
                now,
                false,
                orderIds.size(),
                SYNC_MODE_SPECIFIC,
                resolveSettlementTimeType(GATEWAY_TIME_TYPE_SETTLE),
                activeSettlementSyncSource(),
                cursor -> new DouyinOrderGateway.OrderListResult(List.of(), false, null, Map.of()),
                maxPages,
                maxOrders);
    }

    /**
     * 核心同步循环：逐页拉取订单、归属解析、批量持久化。
     * <p>
     * 每页先 mapOrder 映射原始数据，再通过 AttributionService 解析归属，
     * 随后批量加载用户名填充渠道/招募人名称，最后持久化（新建/更新）。
     * continuePaging=true 时自动翻页直至无更多数据或达到 MAX_PAGES。
     * <p>
     * 完成后输出统一格式的同步日志，包含 mode/timeType/窗口/计数等可观测维度。
     */
    private SyncResult syncItems(
            DouyinOrderGateway.OrderListResult firstPage,
            long startTime,
            long endTime,
            boolean continuePaging,
            int count,
            String mode,
            String timeType,
            SyncSource source,
            Function<String, DouyinOrderGateway.OrderListResult> fetchNextPage) {
        return syncItemsWithLimits(firstPage, startTime, endTime, continuePaging, count, mode, timeType, source,
                fetchNextPage, maxPages, maxOrders);
    }

    private SyncResult syncItemsWithLimits(
            DouyinOrderGateway.OrderListResult firstPage,
            long startTime,
            long endTime,
            boolean continuePaging,
            int count,
            String mode,
            String timeType,
            SyncSource source,
            Function<String, DouyinOrderGateway.OrderListResult> fetchNextPage,
            int pageLimit,
            int orderLimit) {
        String logName = switch (source) {
            case INSTITUTE -> "ORDER_SYNC_INSTITUTE";
            case INSTITUTE_SETTLEMENT -> "ORDER_SYNC_INSTITUTE_SETTLEMENT";
            case SETTLEMENT -> "ORDER_SYNC_SETTLEMENT";
        };
        String api = source == SyncSource.SETTLEMENT ? API_COLONEL_MULTI_SETTLEMENT_ORDERS : API_INSTITUTE_ORDER_COLONEL;
        int maxPageLimit = Math.max(1, pageLimit);
        int maxOrderLimit = Math.max(1, orderLimit);
        String cursor = "0";
        int totalFetched = 0;
        int created = 0;
        int updated = 0;
        int attributedCount = 0;
        int unattributedCount = 0;
        int failedCount = 0;
        int noPickSourceCount = 0;
        int noMappingCount = 0;
        int hasSettleTimeCount = 0;
        int hasEffectiveFeeCount = 0;
        int pages = 0;
        String stopReason = STOP_REASON_UNKNOWN;
        String lastLogId = null;
        Set<String> seenCursors = new LinkedHashSet<>();
        Set<String> seenOrderIds = new LinkedHashSet<>();
        seenCursors.add(cursor);
        DouyinOrderGateway.OrderListResult response = firstPage;

        try {
            while (true) {
                lastLogId = extractLogId(response == null ? null : response.rawResponse());
                List<DouyinOrderGateway.DouyinOrderItem> items = response.orders();
                if (items == null || items.isEmpty()) {
                    stopReason = STOP_REASON_EMPTY_PAGE;
                    break;
                }
                pages++;
                totalFetched += items.size();

                List<ColonelsettlementOrder> pageOrders = new ArrayList<>();
                boolean maxOrdersReached = false;
                int pageFailed = 0;
                for (DouyinOrderGateway.DouyinOrderItem item : items) {
                    if (seenOrderIds.size() >= maxOrderLimit) {
                        maxOrdersReached = true;
                        break;
                    }
                    try {
                        ColonelsettlementOrder order = mapOrder(item, source);
                        if (!StringUtils.hasText(order.getOrderId())) {
                            continue;
                        }
                        if (!seenOrderIds.add(order.getOrderId())) {
                            continue;
                        }
                        orderAttributionRouter.resolveAndApply(order, item.rawPayload(), item.talentName());
                        if (order.getSettleTime() != null) {
                            hasSettleTimeCount++;
                        }
                        if (order.getEffectiveServiceFee() != null && order.getEffectiveServiceFee() > 0) {
                            hasEffectiveFeeCount++;
                        }
                        pageOrders.add(order);
                    } catch (BusinessException e) {
                        failedCount++;
                        pageFailed++;
                        log.warn("Skip order during sync, reason={}, orderId={}", e.getMessage(), item.externalOrderId());
                    } catch (Exception e) {
                        failedCount++;
                        pageFailed++;
                        log.error("Unexpected error processing order, orderId={}, type={}",
                                item.externalOrderId(), e.getClass().getSimpleName(), e);
                    }
                }

                Set<UUID> userIds = new HashSet<>();
                for (ColonelsettlementOrder order : pageOrders) {
                    if (order.getChannelUserId() != null) {
                        userIds.add(order.getChannelUserId());
                    }
                    if (order.getColonelUserId() != null) {
                        userIds.add(order.getColonelUserId());
                    }
                }
                Map<UUID, String> userNamesById = persistenceService.loadUserNamesByIds(userIds);

                int pageCreated = 0;
                int pageUpdated = 0;
                for (ColonelsettlementOrder order : pageOrders) {
                    try {
                        String channelUserName = order.getChannelUserId() == null
                                ? null
                                : userNamesById.get(order.getChannelUserId());
                        if (channelUserName != null) {
                            order.setChannelUserName(channelUserName);
                        }
                        String colonelUserName = order.getColonelUserId() == null
                                ? null
                                : userNamesById.get(order.getColonelUserId());
                        if (colonelUserName != null) {
                            order.setColonelUserName(colonelUserName);
                        }

                        if (orderAttributionRouter.isAttributed(order.getAttributionStatus())) {
                            attributedCount++;
                        } else {
                            unattributedCount++;
                            switch (orderAttributionRouter.classifyUnattributedRemark(order.getAttributionRemark())) {
                                case NO_PICK_SOURCE -> noPickSourceCount++;
                                case NO_MAPPING -> noMappingCount++;
                                default -> {
                                }
                            }
                        }

                        boolean isNew = persistenceService.persistOrder(order);
                        if (isNew) {
                            created++;
                            pageCreated++;
                        } else {
                            updated++;
                            pageUpdated++;
                        }
                    } catch (BusinessException e) {
                        failedCount++;
                        pageFailed++;
                        log.warn("Skip order during sync, reason={}, orderId={}", e.getMessage(), order.getOrderId());
                    } catch (Exception e) {
                        failedCount++;
                        pageFailed++;
                        log.error("Unexpected error persisting order, orderId={}, type={}",
                                order.getOrderId(), e.getClass().getSimpleName(), e);
                    }
                }

                String nextCursor = resolveNextCursor(response);
                log.info("{} api={} mode={} timeType={} pageNo={} cursor={} nextCursor={} logId={} fetched={} "
                                + "inserted={} updated={} failed={}",
                        logName, api, mode, timeType, pages, cursor, nextCursor, lastLogId, items.size(), pageCreated,
                        pageUpdated, pageFailed);
                if (maxOrdersReached || seenOrderIds.size() >= maxOrderLimit) {
                    stopReason = STOP_REASON_MAX_ORDERS;
                    break;
                }
                if (!continuePaging) {
                    stopReason = STOP_REASON_SINGLE_PAGE;
                    break;
                }
                boolean hasNext = response.hasMore() || (isTraversableCursor(nextCursor) && !items.isEmpty());
                if (!hasNext) {
                    stopReason = STOP_REASON_NO_NEXT_CURSOR;
                    break;
                }
                if (pages >= maxPageLimit) {
                    stopReason = STOP_REASON_MAX_PAGES;
                    break;
                }
                if (seenCursors.contains(nextCursor)) {
                    stopReason = STOP_REASON_DUPLICATE_CURSOR;
                    break;
                }
                seenCursors.add(nextCursor);
                cursor = nextCursor;
                response = fetchNextPage.apply(cursor);
            }
        } catch (RuntimeException e) {
            if (STOP_REASON_UNKNOWN.equals(stopReason)) {
                stopReason = STOP_REASON_FETCH_ERROR;
            }
            throw e;
        } finally {
            log.info("{} api={} mode={} timeType={} startTime={} endTime={} range=[{}, {}] pagesFetched={} "
                            + "uniqueOrders={} fetched={} inserted={} updated={} attributed={} unattributed={} "
                            + "noPickSource={} noMapping={} failed={} logId={} hasSettleCount={} "
                            + "hasEffectiveFeeCount={} stopReason={}",
                    logName,
                    api,
                    mode,
                    timeType,
                    formatEpochLog(startTime),
                    formatEpochLog(endTime),
                    startTime, endTime, pages, seenOrderIds.size(), totalFetched, created, updated,
                    attributedCount, unattributedCount, noPickSourceCount, noMappingCount, failedCount, lastLogId,
                    hasSettleTimeCount, hasEffectiveFeeCount, stopReason);
        }

        return new SyncResult(startTime, endTime, pages, totalFetched, created, updated,
                attributedCount, unattributedCount, failedCount, false, seenOrderIds.size(), stopReason);
    }

    private String resolveNextCursor(DouyinOrderGateway.OrderListResult response) {
        if (response == null) {
            return "0";
        }
        Map<String, Object> rawResponse = response.rawResponse();
        return normalizeCursor(firstNonBlank(
                asString(rawPageValue(rawResponse, "cursor")),
                asString(rawValue(rawResponse, "cursor")),
                asString(rawPageValue(rawResponse, "next_cursor", "nextCursor")),
                response.nextCursor()
        ));
    }

    private boolean isTraversableCursor(String cursor) {
        return StringUtils.hasText(cursor) && !"0".equals(cursor);
    }

    private String normalizeCursor(String cursor) {
        return StringUtils.hasText(cursor) ? cursor.trim() : "0";
    }

    @SuppressWarnings("unchecked")
    private Object rawPageValue(Map<String, Object> rawResponse, String... keys) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return null;
        }
        Object data = rawValue(rawResponse, "data");
        if (data instanceof Map<?, ?> dataMap) {
            Object direct = rawValue((Map<String, Object>) dataMap, keys);
            if (direct != null) {
                return direct;
            }
            Object nested = rawValue((Map<String, Object>) dataMap, "data");
            if (nested instanceof Map<?, ?> nestedMap) {
                Object nestedValue = rawValue((Map<String, Object>) nestedMap, keys);
                if (nestedValue != null) {
                    return nestedValue;
                }
            }
        }
        return rawValue(rawResponse, keys);
    }

    /** 按时间范围调用抖音结算网关（受熔断器保护）。 */
    private DouyinOrderGateway.OrderListResult fetchSettlement(DouyinOrderGateway.DouyinOrderQueryRequest request) {
        return executeGatewayCall(() -> toOrderListResult(activeSettlementGateway().fetch(new SettlementOrderQuery(
                formatEpochLog(request.startTime()),
                formatEpochLog(request.endTime()),
                request.resolvedTimeType(),
                request.count(),
                normalizeCursor(request.cursor()),
                List.of(),
                maxPages,
                maxOrders,
                !settlementDualReadNoWriteEnabled))));
    }

    /** 按订单号精确查询抖音结算数据（受熔断器保护）。 */
    private DouyinOrderGateway.OrderListResult fetchSettlementByOrderIds(List<String> orderIds) {
        return executeGatewayCall(() -> toOrderListResult(activeSettlementGateway().fetch(new SettlementOrderQuery(
                null,
                null,
                resolveSettlementTimeType(GATEWAY_TIME_TYPE_SETTLE),
                orderIds == null ? DEFAULT_COUNT : Math.min(Math.max(orderIds.size(), 1), DEFAULT_COUNT),
                "0",
                normalizeOrderIds(orderIds),
                1,
                orderIds == null ? DEFAULT_COUNT : Math.min(Math.max(orderIds.size(), 1), DEFAULT_COUNT),
                !settlementDualReadNoWriteEnabled))));
    }

    private SettlementOrderGateway activeSettlementGateway() {
        String source = settlementSource == null ? "" : settlementSource.trim();
        if ("colonelMultiSettlementOrders".equalsIgnoreCase(source)) {
            return multiSettlementFallbackGateway;
        }
        if ("dualReadNoWrite".equalsIgnoreCase(source)) {
            return instituteSettlementGateway;
        }
        return instituteSettlementGateway;
    }

    private SyncSource activeSettlementSyncSource() {
        String source = settlementSource == null ? "" : settlementSource.trim();
        if ("colonelMultiSettlementOrders".equalsIgnoreCase(source)) {
            return SyncSource.SETTLEMENT;
        }
        return SyncSource.INSTITUTE_SETTLEMENT;
    }

    private String resolveSettlementTimeType(String requestedTimeType) {
        String requested = StringUtils.hasText(requestedTimeType)
                ? requestedTimeType.trim().toLowerCase()
                : GATEWAY_TIME_TYPE_SETTLE;
        if (!GATEWAY_TIME_TYPE_SETTLE.equals(requested)) {
            return requested;
        }
        if (StringUtils.hasText(instituteSettlementTimeType)
                && activeSettlementSyncSource() == SyncSource.INSTITUTE_SETTLEMENT) {
            return instituteSettlementTimeType.trim().toLowerCase();
        }
        return requested;
    }

    private DouyinOrderGateway.OrderListResult toOrderListResult(SettlementOrderPage page) {
        if (page == null) {
            return new DouyinOrderGateway.OrderListResult(List.of(), false, "0", Map.of());
        }
        List<DouyinOrderGateway.DouyinOrderItem> orders = page.orders() == null
                ? List.of()
                : page.orders().stream()
                        .map(this::toSettlementOrderItem)
                        .filter(item -> StringUtils.hasText(item.externalOrderId()))
                        .toList();
        Map<String, Object> rawResponse = page.rawResponse() == null || page.rawResponse().isMissingNode()
                ? new java.util.LinkedHashMap<>()
                : OBJECT_MAPPER.convertValue(page.rawResponse(), MAP_TYPE);
        rawResponse.put("apiMethod", page.apiMethod());
        rawResponse.put("source", page.source());
        return new DouyinOrderGateway.OrderListResult(
                orders,
                page.hasMore(),
                StringUtils.hasText(page.nextCursor()) ? page.nextCursor() : "0",
                rawResponse);
    }

    private DouyinOrderGateway.DouyinOrderItem toSettlementOrderItem(JsonNode node) {
        Map<String, Object> raw = node == null || node.isMissingNode() || node.isNull()
                ? new java.util.LinkedHashMap<>()
                : OBJECT_MAPPER.convertValue(node, MAP_TYPE);
        raw = AttributionSourceNormalizer.normalize(raw);
        return new DouyinOrderGateway.DouyinOrderItem(
                asString(rawValue(raw, "order_id", "orderId", "order_id_str", "orderIdStr")),
                asString(rawValue(raw, "external_product_id", "externalProductId", "product_id", "productId")),
                asString(rawValue(raw, "product_id", "productId")),
                asString(rawValue(raw, "merchant_id", "merchantId", "shop_id", "shopId")),
                asString(rawValue(raw, "merchant_name", "merchantName", "shop_name", "shopName")),
                asString(rawValue(raw, "talent_id", "talentId", "talent_uid", "talentUid",
                        "author_id", "authorId", "author_buyin_id", "authorBuyinId")),
                asString(rawValue(raw, "talent_name", "talentName", "author_name", "authorName",
                        "author_account", "authorAccount")),
                asString(rawValue(raw, "pick_source", "pickSource")),
                asNullableLong(rawValue(raw, "order_amount", "orderAmount", "total_amount", "totalAmount",
                        "pay_amount", "payAmount", "total_pay_amount", "totalPayAmount",
                        "pay_goods_amount", "payGoodsAmount")),
                asNullableLong(rawOrderInfoValue(raw, "real_commission", "realCommission", "settled_commission",
                        "settledCommission", "commission", "institution_commission", "institutionCommission",
                        "colonel_commission", "colonelCommission", "service_fee", "serviceFee")),
                asNullableInteger(rawValue(raw, "order_status", "orderStatus", "status")),
                asEpochSecond(rawValue(raw, "create_time", "createTime", "order_create_time", "orderCreateTime",
                        "pay_success_time", "paySuccessTime", "pay_time", "payTime")),
                asEpochSecond(rawValue(raw, "settle_time", "settleTime", "settled_time", "settledTime")),
                raw);
    }

    /** 执行网关调用，成功重置失败计数，失败累加并可能触发熔断。 */
    private DouyinOrderGateway.OrderListResult executeGatewayCall(Supplier<DouyinOrderGateway.OrderListResult> call) {
        assertGatewayCircuitClosed();
        try {
            DouyinOrderGateway.OrderListResult result = call.get();
            consecutiveGatewayFailures.set(0);
            gatewayCircuitOpenedAt = null;
            return result;
        } catch (RuntimeException ex) {
            recordGatewayFailure(ex);
            throw ex;
        }
    }

    /** 检查熔断器状态：处于 OPEN 且冷却期未过则抛出外部异常，否则自动恢复。 */
    private void assertGatewayCircuitClosed() {
        Instant openedAt = gatewayCircuitOpenedAt;
        if (openedAt == null) {
            return;
        }
        Duration openDuration = gatewayCircuitOpenDuration == null ? Duration.ofMinutes(5) : gatewayCircuitOpenDuration;
        Instant retryAfter = openedAt.plus(openDuration);
        if (Instant.now().isBefore(retryAfter)) {
            throw BusinessException.external("Order sync upstream circuit is open");
        }
        consecutiveGatewayFailures.set(0);
        gatewayCircuitOpenedAt = null;
    }

    /** 记录网关调用失败，累计达到阈值时打开熔断器。 */
    private void recordGatewayFailure(RuntimeException ex) {
        int threshold = Math.max(1, gatewayFailureThreshold);
        int failures = consecutiveGatewayFailures.incrementAndGet();
        if (failures >= threshold) {
            gatewayCircuitOpenedAt = Instant.now();
            log.error("Order sync upstream circuit opened, consecutiveFailures={}, threshold={}, exception={}",
                    failures, threshold, ex.getClass().getSimpleName());
        }
    }

    /**
     * 将抖音网关返回的原始订单项映射为本地 ColonelsettlementOrder 实体。
     * <p>
     * 从 rawPayload 中提取商品名、店铺、团长信息、金额（双轨）、
     * 派单来源等字段；归属状态初始化为 UNATTRIBUTED。
     */
    private ColonelsettlementOrder mapOrder(DouyinOrderGateway.DouyinOrderItem item, SyncSource source) {
        Map<String, Object> rawPayload = item.rawPayload();
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId(item.externalOrderId());
        order.setProductId(item.productId());
        order.setProductName(rawPayload != null ? asString(rawValue(rawPayload, "product_name", "productName")) : null);
        order.setShopId(parseMerchantId(item.merchantId()));
        order.setShopName(item.merchantName());
        LocalDateTime itemSettleTime = item.settleTime() == null ? null : AppZone.fromEpochSecond(item.settleTime());
        orderAmountMappingRouter.mapAndApplyToOrder(
                source,
                order,
                rawPayload,
                item.orderAmount(),
                item.serviceFee(),
                itemSettleTime);
        if (source == SyncSource.INSTITUTE) {
            order.setSyncSource(OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE);
        } else if (source == SyncSource.INSTITUTE_SETTLEMENT) {
            order.setSyncSource(OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT);
        } else {
            order.setSyncSource(OrderSyncPersistenceService.SYNC_SOURCE_SETTLEMENT);
        }
        order.setColonelBuyinId(asNullableLong(rawOrderInfoValue(rawPayload, "colonel_buyin_id", "colonelBuyinId")));
        order.setSecondColonelBuyinId(asNullableLong(rawOrderInfoValue(rawPayload, "second_colonel_buyin_id", "secondColonelBuyinId")));
        order.setSecondActivityId(asString(rawOrderInfoValue(rawPayload, "second_colonel_activity_id", "secondColonelActivityId")));
        order.setPhaseId(asString(rawValue(rawPayload, "phase_id", "phaseId")));
        order.setOrderStatus(item.orderStatus());
        order.setFlowPoint(asString(rawValue(rawPayload, "flow_point", "flowPoint")));
        order.setOrderType(asNullableInteger(rawValue(rawPayload, "order_type", "orderType")));
        order.setPickSource(item.pickSource());
        order.setCursor(asString(rawValue(rawPayload, "cursor")));
        LocalDateTime orderCreateTime = rawDateTime(rawPayload, "order_create_time", "orderCreateTime", "create_time", "createTime");
        LocalDateTime payTime = rawDateTime(rawPayload, "pay_success_time", "paySuccessTime", "pay_time", "payTime");
        LocalDateTime itemCreateTime = item.createTime() == null ? null : AppZone.fromEpochSecond(item.createTime());
        order.setOrderCreateTime(firstNonNullTime(orderCreateTime, itemCreateTime));
        order.setPayTime(payTime);
        order.setCreateTime(firstNonNullTime(payTime, orderCreateTime, itemCreateTime, LocalDateTime.now()));
        if (source != SyncSource.INSTITUTE) {
            LocalDateTime settleTime = rawDateTime(rawPayload, "settle_time", "settleTime");
            if (settleTime == null && item.settleTime() != null) {
                settleTime = AppZone.fromEpochSecond(item.settleTime());
            }
            if (settleTime != null) {
                order.setSettleTime(settleTime);
            }
        }
        orderAttributionRouter.applyInitialUnattributedStatus(order);
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(0);
        order.setExtraData(rawPayload);
        return order;
    }

    /** 将商家 ID 字符串中的非数字字符去除后解析为 Long，无效时返回 null。 */
    private Long parseMerchantId(String merchantId) {
        if (merchantId == null) {
            return null;
        }
        String digits = merchantId.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        return Long.parseLong(digits);
    }

    /** 返回第一个非空白字符串，全部为空白时返回 null。 */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /** 返回第一个非 null 时间。 */
    private LocalDateTime firstNonNullTime(LocalDateTime... values) {
        if (values == null) {
            return null;
        }
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** 将任意对象转为 String，null 安全。 */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 从 rawPayload 中按候选键解析时间，兼容 epoch 秒、epoch 毫秒和 yyyy-MM-dd HH:mm:ss 字符串。 */
    private LocalDateTime rawDateTime(Map<String, Object> rawPayload, String... keys) {
        return asDateTime(rawValue(rawPayload, keys));
    }

    private Long asEpochSecond(Object value) {
        LocalDateTime dateTime = asDateTime(value);
        return dateTime == null ? null : AppZone.toEpochSecond(dateTime);
    }

    /** 将上游时间值转换为应用时区 LocalDateTime。 */
    private LocalDateTime asDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            return raw > 9_999_999_999L ? AppZone.fromEpochMilli(raw) : AppZone.fromEpochSecond(raw);
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            long raw = Long.parseLong(text);
            return raw > 9_999_999_999L ? AppZone.fromEpochMilli(raw) : AppZone.fromEpochSecond(raw);
        } catch (NumberFormatException ignore) {
            // Fall through to formatted date parsing.
        }
        try {
            return LocalDateTime.parse(text, RAW_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    /** 在 rawPayload 中按 keys 顺序查找，返回第一个命中的值，均未命中返回 null。 */
    private Object rawValue(Map<String, Object> rawPayload, String... keys) {
        if (rawPayload == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (rawPayload.containsKey(key)) {
                return rawPayload.get(key);
            }
        }
        return null;
    }

    /** 先在顶层 rawPayload 按 keys 查找，未命中时回退到嵌套的 colonel_order_info 子 Map 中查找。 */
    @SuppressWarnings("unchecked")
    private Object rawOrderInfoValue(Map<String, Object> rawPayload, String... keys) {
        Object value = rawValue(rawPayload, keys);
        if (value != null) {
            return value;
        }
        Object nested = rawValue(rawPayload, "colonel_order_info", "colonelOrderInfo");
        if (nested instanceof Map<?, ?> map) {
            return rawValue((Map<String, Object>) map, keys);
        }
        return null;
    }

    /** 将 Number 或可解析的字符串转为 Long，null 或解析失败时返回 null。 */
    private Long asNullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    /** 委托 asNullableLong 解析后窄化为 Integer，null 安全。 */
    private Integer asNullableInteger(Object value) {
        Long parsed = asNullableLong(value);
        return parsed == null ? null : parsed.intValue();
    }

    /** 将订单 ID 列表去重、trim 并返回不可变副本，空列表返回 List.of()。 */
    private List<String> normalizeOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String orderId : orderIds) {
            if (StringUtils.hasText(orderId)) {
                normalized.add(orderId.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private String extractLogId(Map<String, Object> rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return null;
        }
        Object logId = rawValue(rawResponse, "log_id", "logId");
        return logId == null ? null : String.valueOf(logId);
    }

    private String formatEpochLog(long epochSecond) {
        if (epochSecond <= 0L) {
            return null;
        }
        return RAW_DATE_TIME_FORMATTER.format(AppZone.fromEpochSecond(epochSecond));
    }

    /** 将任意对象解析为 long，null 或解析失败时返回 defaultValue。 */
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

    /** 同步结果摘要：记录本次同步的时间窗口、分页数、新增/更新/归属统计，以及是否因分布式锁跳过。 */
    public record SyncResult(
            long startTime,
            long endTime,
            int pages,
            int totalFetched,
            int created,
            int updated,
            int attributed,
            int unattributed,
            int failed,
            boolean locked,
            int uniqueOrders,
            String stopReason) {

        public SyncResult(long startTime, long endTime, int pages, int totalFetched, int created, int updated,
                          int attributed, int unattributed, int failed, boolean locked) {
            this(startTime, endTime, pages, totalFetched, created, updated, attributed, unattributed, failed, locked,
                    totalFetched, locked ? STOP_REASON_LOCKED : STOP_REASON_UNKNOWN);
        }

        public SyncResult(long startTime, long endTime, int pages, int inserted, int skipped, boolean locked) {
            this(startTime, endTime, pages, inserted + skipped, inserted, 0, 0, skipped, 0, locked,
                    inserted + skipped, locked ? STOP_REASON_LOCKED : STOP_REASON_UNKNOWN);
        }

        public int inserted() {
            return created;
        }

        public int skipped() {
            return unattributed;
        }
    }
}
