package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.common.exception.BusinessException;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class OrderSyncService {

    private static final String LAST_SYNC_TIME_KEY = "order:sync:last_time";
    private static final String SYNC_LOCK_KEY = "order:sync:lock";
    private static final long WINDOW_SECONDS = 600L;
    private static final long OVERLAP_SECONDS = 60L;
    private static final long LAG_SECONDS = 60L;
    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_PAGES = 200;

    private final DouyinOrderGateway douyinOrderGateway;
    private final OrderSyncPersistenceService persistenceService;
    private final AttributionService attributionService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean testEnabled;
    private final AtomicBoolean localLock = new AtomicBoolean(false);
    private volatile long localLastSyncTime;

    public OrderSyncService(
            DouyinOrderGateway douyinOrderGateway,
            OrderSyncPersistenceService persistenceService,
            AttributionService attributionService,
            RedisTemplate<String, Object> redisTemplate,
            @Value("${app.test.enabled:false}") boolean testEnabled) {
        this.douyinOrderGateway = douyinOrderGateway;
        this.persistenceService = persistenceService;
        this.attributionService = attributionService;
        this.redisTemplate = redisTemplate;
        this.testEnabled = testEnabled;
    }

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

    public SyncResult triggerManualSync() {
        return syncLatestWindow();
    }

    public LocalDateTime getLastSyncTime() {
        long epochSecond = readLastSyncTime();
        if (epochSecond <= 0L) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault());
    }

    public SyncResult syncByTimeRange(long startTime, long endTime) {
        if (startTime <= 0 || endTime <= 0 || startTime >= endTime) {
            throw new BusinessException("Invalid sync time range");
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

    private long readLastSyncTime() {
        try {
            Object raw = redisTemplate.opsForValue().get(LAST_SYNC_TIME_KEY);
            return asLong(raw, localLastSyncTime);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when reading last sync time, fallback to local state: {}", ex.getMessage());
                return localLastSyncTime;
            }
            throw ex;
        }
    }

    private boolean acquireSyncLock() {
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    Objects.requireNonNull(SYNC_LOCK_KEY),
                    "1",
                    Objects.requireNonNull(Duration.ofMinutes(10))
            );
            return Boolean.TRUE.equals(locked);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when acquiring sync lock, fallback to local lock: {}", ex.getMessage());
                return localLock.compareAndSet(false, true);
            }
            throw ex;
        }
    }

    private void persistLastSyncTime(long endTime) {
        localLastSyncTime = endTime;
        try {
            redisTemplate.opsForValue().set(LAST_SYNC_TIME_KEY, String.valueOf(endTime));
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when persisting last sync time, keep local state only: {}", ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    private void releaseSyncLock() {
        localLock.set(false);
        try {
            redisTemplate.delete(SYNC_LOCK_KEY);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when releasing sync lock, local lock already released: {}", ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    private SyncResult syncRange(long startTime, long endTime, int count) {
        String cursor = "0";
        boolean hasMore = true;
        int totalFetched = 0;
        int created = 0;
        int updated = 0;
        int attributedCount = 0;
        int unattributedCount = 0;
        int failedCount = 0;
        int pages = 0;

        while (hasMore) {
            DouyinOrderGateway.OrderListResult response = douyinOrderGateway.listSettlement(
                    new DouyinOrderGateway.DouyinOrderQueryRequest(startTime, endTime, count, cursor)
            );
            List<DouyinOrderGateway.DouyinOrderItem> items = response.orders();
            if (items == null || items.isEmpty()) {
                break;
            }
            pages++;
            totalFetched += items.size();

            for (DouyinOrderGateway.DouyinOrderItem item : items) {
                try {
                    ColonelsettlementOrder order = mapOrder(item);
                    if (!StringUtils.hasText(order.getOrderId())) {
                        continue;
                    }
                    AttributionService.AttributionResult attribution = attributionService.resolveAttribution(order, item.rawPayload());
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
                    // talent_name 应由达人信息补全，此处不写入不可靠的uid值

                    // 补全人名
                    if (order.getChannelUserId() != null) {
                        SysUser u = persistenceService.getUser(order.getChannelUserId());
                        if (u != null) order.setChannelUserName(u.getRealName());
                    }
                    if (order.getColonelUserId() != null) {
                        SysUser u = persistenceService.getUser(order.getColonelUserId());
                        if (u != null) order.setColonelUserName(u.getRealName());
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
                    log.warn("Skip order during sync, reason={}, orderId={}", e.getMessage(), item.externalOrderId());
                } catch (Exception e) {
                    failedCount++;
                    log.error("Unexpected error processing order, orderId={}, type={}", item.externalOrderId(), e.getClass().getSimpleName(), e);
                }
            }

            cursor = response.nextCursor();
            hasMore = response.hasMore() && pages < MAX_PAGES;
        }

        log.info("Order sync completed, range=[{}, {}], pages={}, fetched={}, created={}, updated={}, attributed={}",
                startTime, endTime, pages, totalFetched, created, updated, attributedCount);

        return new SyncResult(startTime, endTime, pages, totalFetched, created, updated, attributedCount, unattributedCount, failedCount, false);
    }

    private ColonelsettlementOrder mapOrder(DouyinOrderGateway.DouyinOrderItem item) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId(item.externalOrderId());
        order.setProductId(item.productId());
        order.setProductName(item.rawPayload() != null ? asString(item.rawPayload().get("product_name")) : null);
        order.setShopId(parseMerchantId(item.merchantId()));
        order.setShopName(item.merchantName());
        order.setOrderAmount(item.orderAmount());
        order.setActualAmount(resolveActualAmount(item));
        order.setSettleColonelCommission(item.serviceFee());
        order.setOrderStatus(item.orderStatus());
        order.setPickSource(item.pickSource());
        order.setCreateTime(LocalDateTime.ofInstant(Instant.ofEpochSecond(item.createTime()), ZoneId.systemDefault()));
        if (item.settleTime() != null) {
            order.setSettleTime(LocalDateTime.ofInstant(Instant.ofEpochSecond(item.settleTime()), ZoneId.systemDefault()));
        }
        order.setAttributionStatus("UNATTRIBUTED");
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(0);
        order.setExtraData(item.rawPayload());
        return order;
    }

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

    private Long resolveActualAmount(DouyinOrderGateway.DouyinOrderItem item) {
        if (item.rawPayload() != null) {
            Object actual = item.rawPayload().get("actual_amount");
            if (actual instanceof Number number) {
                return number.longValue();
            }
        }
        return item.orderAmount();
    }

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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
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


