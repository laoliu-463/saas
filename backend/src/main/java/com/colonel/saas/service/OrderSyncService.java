package com.colonel.saas.service;

import com.colonel.saas.config.AppProperties;
import com.colonel.saas.job.JobLockKeys;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.time.AppZone;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SysUser;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 订单同步服务：从抖音结算网关拉取最新订单并持久化。
 * <p>
 * 支持按时间窗口自动同步、手动触发、按订单号精确同步三种模式；
 * 内置分布式锁（Redis）防并发，熔断器保护上游网关；同步过程中
 * 通过 {@link AttributionService} 完成订单归属解析。
 */
@Slf4j
@Service
public class OrderSyncService {

    /** Redis key 存储上次同步的截止时间（epoch seconds）。 */
    private static final String LAST_SYNC_TIME_KEY = "order:sync:last_time";
    /** 分布式同步锁 TTL，防止同步任务长时间占用。 */
    private static final Duration SYNC_LOCK_TTL = Duration.ofMinutes(10);
    /** 默认同步时间窗口长度（秒），约 10 分钟。 */
    private static final long WINDOW_SECONDS = 600L;
    /** 时间窗口重叠（秒），用于补偿边界订单。 */
    private static final long OVERLAP_SECONDS = 60L;
    /** 相对当前时间的滞后（秒），避免查询未稳定的上游数据。 */
    private static final long LAG_SECONDS = 60L;
    /** 每页请求默认拉取数量。 */
    private static final int DEFAULT_COUNT = 100;
    /** 最大翻页次数，超过后强制停止，防止无限循环。 */
    private static final int MAX_PAGES = 200;

    private final DouyinOrderGateway douyinOrderGateway;
    private final OrderSyncPersistenceService persistenceService;
    private final AttributionService attributionService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedJobLockService jobLockService;
    private final AppProperties appProperties;
    private volatile long localLastSyncTime;
    private final AtomicInteger consecutiveGatewayFailures = new AtomicInteger();
    private volatile Instant gatewayCircuitOpenedAt;

    @Value("${order.sync.circuit-breaker.failure-threshold:3}")
    private int gatewayFailureThreshold = 3;

    @Value("${order.sync.circuit-breaker.open-duration:PT5M}")
    private Duration gatewayCircuitOpenDuration = Duration.ofMinutes(5);

    public OrderSyncService(
            DouyinOrderGateway douyinOrderGateway,
            OrderSyncPersistenceService persistenceService,
            AttributionService attributionService,
            RedisTemplate<String, Object> redisTemplate,
            DistributedJobLockService jobLockService,
            AppProperties appProperties) {
        this.douyinOrderGateway = douyinOrderGateway;
        this.persistenceService = persistenceService;
        this.attributionService = attributionService;
        this.redisTemplate = redisTemplate;
        this.jobLockService = jobLockService;
        this.appProperties = appProperties;
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
        long endTime = now - LAG_SECONDS;
        long defaultStart = endTime - WINDOW_SECONDS - OVERLAP_SECONDS;
        long startTime = defaultStart;
        long lastSyncTime = readLastSyncTime();
        if (lastSyncTime > 0L) {
            startTime = Math.max(0L, lastSyncTime - OVERLAP_SECONDS);
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
            SyncResult result = syncRange(startTime, endTime, DEFAULT_COUNT);
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

    /** 按时间窗口同步：构造首屏查询请求并委托到 syncItems 处理翻页。 */
    private SyncResult syncRange(long startTime, long endTime, int count) {
        return syncItems(
                fetchSettlement(new DouyinOrderGateway.DouyinOrderQueryRequest(startTime, endTime, count, "0")),
                startTime,
                endTime,
                true,
                count
        );
    }

    /** 按订单号精确同步：调用网关精确查询接口，不翻页。 */
    private SyncResult syncSpecificOrders(List<String> orderIds) {
        long now = Instant.now().getEpochSecond();
        return syncItems(
                fetchSettlementByOrderIds(orderIds),
                now,
                now,
                false,
                orderIds.size()
        );
    }

    /**
     * 核心同步循环：逐页拉取订单、归属解析、批量持久化。
     * <p>
     * 每页先 mapOrder 映射原始数据，再通过 AttributionService 解析归属，
     * 随后批量加载用户名填充渠道/招募人名称，最后持久化（新建/更新）。
     * continuePaging=true 时自动翻页直至无更多数据或达到 MAX_PAGES。
     */
    private SyncResult syncItems(
            DouyinOrderGateway.OrderListResult firstPage,
            long startTime,
            long endTime,
            boolean continuePaging,
            int count) {
        String cursor = "0";
        boolean hasMore = true;
        int totalFetched = 0;
        int created = 0;
        int updated = 0;
        int attributedCount = 0;
        int unattributedCount = 0;
        int failedCount = 0;
        int pages = 0;
        DouyinOrderGateway.OrderListResult response = firstPage;

        while (hasMore) {
            List<DouyinOrderGateway.DouyinOrderItem> items = response.orders();
            if (items == null || items.isEmpty()) {
                break;
            }
            pages++;
            totalFetched += items.size();

            List<ColonelsettlementOrder> pageOrders = new ArrayList<>();
            for (DouyinOrderGateway.DouyinOrderItem item : items) {
                try {
                    ColonelsettlementOrder order = mapOrder(item);
                    if (!StringUtils.hasText(order.getOrderId())) {
                        continue;
                    }
                    AttributionService.AttributionResult attribution =
                            attributionService.resolveAttribution(order, item.rawPayload());
                    order.setChannelUserId(attribution.channelUserId());
                    order.setChannelDeptId(attribution.deptId());
                    order.setUserId(attribution.userId());
                    order.setDeptId(attribution.deptId());
                    order.setColonelUserId(attribution.colonelUserId());
                    order.setTalentId(attribution.talentId());
                    order.setActivityId(firstNonBlank(attribution.activityId(), order.getActivityId()));
                    order.setAttributionStatus(attribution.attributionStatus());
                    order.setAttributionRemark(attribution.attributionRemark());
                    order.setProductTitle(order.getProductName());
                    order.setTalentName(item.talentName());
                    pageOrders.add(order);
                } catch (BusinessException e) {
                    failedCount++;
                    log.warn("Skip order during sync, reason={}, orderId={}", e.getMessage(), item.externalOrderId());
                } catch (Exception e) {
                    failedCount++;
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
            Map<UUID, SysUser> usersById = persistenceService.loadUsersByIds(userIds);

            for (ColonelsettlementOrder order : pageOrders) {
                try {
                    SysUser channelUser = usersById.get(order.getChannelUserId());
                    if (channelUser != null) {
                        order.setChannelUserName(channelUser.getRealName());
                    }
                    SysUser colonelUser = usersById.get(order.getColonelUserId());
                    if (colonelUser != null) {
                        order.setColonelUserName(colonelUser.getRealName());
                    }

                    if ("ATTRIBUTED".equals(order.getAttributionStatus())) {
                        attributedCount++;
                    } else {
                        unattributedCount++;
                    }

                    boolean isNew = persistenceService.persistOrder(order);
                    if (isNew) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (BusinessException e) {
                    failedCount++;
                    log.warn("Skip order during sync, reason={}, orderId={}", e.getMessage(), order.getOrderId());
                } catch (Exception e) {
                    failedCount++;
                    log.error("Unexpected error persisting order, orderId={}, type={}",
                            order.getOrderId(), e.getClass().getSimpleName(), e);
                }
            }

            cursor = response.nextCursor();
            hasMore = continuePaging && response.hasMore() && pages < MAX_PAGES;
            if (hasMore) {
                response = fetchSettlement(
                        new DouyinOrderGateway.DouyinOrderQueryRequest(startTime, endTime, count, cursor)
                );
            }
        }

        log.info("Order sync completed, range=[{}, {}], pages={}, fetched={}, created={}, updated={}, attributed={}",
                startTime, endTime, pages, totalFetched, created, updated, attributedCount);

        return new SyncResult(startTime, endTime, pages, totalFetched, created, updated, attributedCount, unattributedCount, failedCount, false);
    }

    /** 按时间范围调用抖音结算网关（受熔断器保护）。 */
    private DouyinOrderGateway.OrderListResult fetchSettlement(DouyinOrderGateway.DouyinOrderQueryRequest request) {
        return executeGatewayCall(() -> douyinOrderGateway.listSettlement(request));
    }

    /** 按订单号精确查询抖音结算数据（受熔断器保护）。 */
    private DouyinOrderGateway.OrderListResult fetchSettlementByOrderIds(List<String> orderIds) {
        return executeGatewayCall(() -> douyinOrderGateway.listSettlementByOrderIds(orderIds));
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
    private ColonelsettlementOrder mapOrder(DouyinOrderGateway.DouyinOrderItem item) {
        Map<String, Object> rawPayload = item.rawPayload();
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId(item.externalOrderId());
        order.setProductId(item.productId());
        order.setProductName(rawPayload != null ? asString(rawValue(rawPayload, "product_name", "productName")) : null);
        order.setShopId(parseMerchantId(item.merchantId()));
        order.setShopName(item.merchantName());
        OrderDualTrackAmountResolver.DualTrackAmounts dualTrack = OrderDualTrackAmountResolver.resolve(
                rawPayload,
                item.orderAmount(),
                item.serviceFee());
        OrderDualTrackAmountResolver.applyToOrder(order, dualTrack);
        order.setColonelBuyinId(asNullableLong(rawOrderInfoValue(rawPayload, "colonel_buyin_id", "colonelBuyinId")));
        order.setSecondColonelBuyinId(asNullableLong(rawOrderInfoValue(rawPayload, "second_colonel_buyin_id", "secondColonelBuyinId")));
        order.setSecondActivityId(asString(rawOrderInfoValue(rawPayload, "second_colonel_activity_id", "secondColonelActivityId")));
        order.setPhaseId(asString(rawValue(rawPayload, "phase_id", "phaseId")));
        order.setOrderStatus(item.orderStatus());
        order.setOrderType(asNullableInteger(rawValue(rawPayload, "order_type", "orderType")));
        order.setPickSource(item.pickSource());
        order.setCursor(asString(rawValue(rawPayload, "cursor")));
        order.setCreateTime(AppZone.fromEpochSecond(item.createTime()));
        if (item.settleTime() != null) {
            order.setSettleTime(AppZone.fromEpochSecond(item.settleTime()));
        }
        order.setAttributionStatus("UNATTRIBUTED");
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

    /** 从 rawPayload 中读取 actual_amount，不存在时回退到 item.orderAmount()。 */
    private Long resolveActualAmount(DouyinOrderGateway.DouyinOrderItem item) {
        if (item.rawPayload() != null) {
            Object actual = item.rawPayload().get("actual_amount");
            if (actual instanceof Number number) {
                return number.longValue();
            }
        }
        return item.orderAmount();
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

    /** 将任意对象转为 String，null 安全。 */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
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
            boolean locked) {

        public SyncResult(long startTime, long endTime, int pages, int inserted, int skipped, boolean locked) {
            this(startTime, endTime, pages, inserted + skipped, inserted, 0, 0, skipped, 0, locked);
        }

        public int inserted() {
            return created;
        }

        public int skipped() {
            return unattributed;
        }
    }
}
