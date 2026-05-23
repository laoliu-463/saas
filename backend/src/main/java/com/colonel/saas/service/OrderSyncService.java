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

@Slf4j
@Service
public class OrderSyncService {

    private static final String LAST_SYNC_TIME_KEY = "order:sync:last_time";
    private static final Duration SYNC_LOCK_TTL = Duration.ofMinutes(10);
    private static final long WINDOW_SECONDS = 600L;
    private static final long OVERLAP_SECONDS = 60L;
    private static final long LAG_SECONDS = 60L;
    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_PAGES = 200;

    private final DouyinOrderGateway douyinOrderGateway;
    private final OrderSyncPersistenceService persistenceService;
    private final AttributionService attributionService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedJobLockService jobLockService;
    private final AppProperties appProperties;
    private volatile long localLastSyncTime;

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

    private boolean testEnabled() {
        return appProperties.getTest().isEnabled();
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

    public LocalDateTime getLastSyncTime() {
        long epochSecond = readLastSyncTime();
        if (epochSecond <= 0L) {
            return null;
        }
        return AppZone.fromEpochSecond(epochSecond);
    }

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

    private boolean acquireSyncLock() {
        return jobLockService.tryAcquireStrict(JobLockKeys.ORDER_SYNC, SYNC_LOCK_TTL);
    }

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

    private void releaseSyncLock() {
        jobLockService.release(JobLockKeys.ORDER_SYNC);
    }

    private SyncResult syncRange(long startTime, long endTime, int count) {
        return syncItems(
                douyinOrderGateway.listSettlement(new DouyinOrderGateway.DouyinOrderQueryRequest(startTime, endTime, count, "0")),
                startTime,
                endTime,
                true,
                count
        );
    }

    private SyncResult syncSpecificOrders(List<String> orderIds) {
        long now = Instant.now().getEpochSecond();
        return syncItems(
                douyinOrderGateway.listSettlementByOrderIds(orderIds),
                now,
                now,
                false,
                orderIds.size()
        );
    }

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
                response = douyinOrderGateway.listSettlement(
                        new DouyinOrderGateway.DouyinOrderQueryRequest(startTime, endTime, count, cursor)
                );
            }
        }

        log.info("Order sync completed, range=[{}, {}], pages={}, fetched={}, created={}, updated={}, attributed={}",
                startTime, endTime, pages, totalFetched, created, updated, attributedCount);

        return new SyncResult(startTime, endTime, pages, totalFetched, created, updated, attributedCount, unattributedCount, failedCount, false);
    }

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

    private Integer asNullableInteger(Object value) {
        Long parsed = asNullableLong(value);
        return parsed == null ? null : parsed.intValue();
    }

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
