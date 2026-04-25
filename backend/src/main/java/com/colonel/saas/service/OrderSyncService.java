package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SysUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    public OrderSyncService(
            DouyinOrderGateway douyinOrderGateway,
            OrderSyncPersistenceService persistenceService,
            AttributionService attributionService,
            RedisTemplate<String, Object> redisTemplate) {
        this.douyinOrderGateway = douyinOrderGateway;
        this.persistenceService = persistenceService;
        this.attributionService = attributionService;
        this.redisTemplate = redisTemplate;
    }

    public SyncResult syncLatestWindow() {
        long now = Instant.now().getEpochSecond();
        long endTime = now - LAG_SECONDS;
        long defaultStart = endTime - WINDOW_SECONDS - OVERLAP_SECONDS;
        long startTime = defaultStart;
        Object lastSyncRaw = redisTemplate.opsForValue().get(LAST_SYNC_TIME_KEY);
        long lastSyncTime = asLong(lastSyncRaw, 0L);
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

    public SyncResult syncByTimeRange(long startTime, long endTime) {
        if (startTime <= 0 || endTime <= 0 || startTime >= endTime) {
            throw new BusinessException("Invalid sync time range");
        }
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                Objects.requireNonNull(SYNC_LOCK_KEY),
                "1",
                Objects.requireNonNull(Duration.ofMinutes(10))
        );
        if (!Boolean.TRUE.equals(locked)) {
            return new SyncResult(startTime, endTime, 0, 0, 0, true);
        }
        try {
            SyncResult result = syncRange(startTime, endTime, DEFAULT_COUNT);
            redisTemplate.opsForValue().set(LAST_SYNC_TIME_KEY, String.valueOf(endTime));
            return result;
        } finally {
            redisTemplate.delete(SYNC_LOCK_KEY);
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
                    order.setActivityId(firstNonBlank(attribution.activityId(), order.getActivityId()));
                    order.setAttributionStatus(attribution.attributionStatus());
                    order.setAttributionRemark(attribution.attributionRemark());
                    order.setProductTitle(order.getProductName());
                    order.setTalentName(attribution.talentUid()); // Fallback or mapping

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
                } catch (Exception e) {
                    log.warn("Skip order during sync, reason={}, orderId={}", e.getMessage(), item.externalOrderId());
                }
            }

            cursor = response.nextCursor();
            hasMore = response.hasMore() && pages < MAX_PAGES;
        }

        log.info("Order sync completed, range=[{}, {}], pages={}, fetched={}, created={}, updated={}, attributed={}",
                startTime, endTime, pages, totalFetched, created, updated, attributedCount);

        return new SyncResult(startTime, endTime, pages, totalFetched, created, updated, attributedCount, unattributedCount, false);
    }

    private ColonelsettlementOrder mapOrder(DouyinOrderGateway.DouyinOrderItem item) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId(item.externalOrderId());
        order.setProductId(item.productId());
        order.setProductName(item.rawPayload() != null ? asString(item.rawPayload().get("product_name")) : null);
        order.setShopId(item.merchantId() != null ? Long.parseLong(item.merchantId().replaceAll("\\D", "")) : null);
        order.setShopName(item.merchantName());
        order.setOrderAmount(item.orderAmount());
        order.setActualAmount(item.orderAmount()); // Fallback
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

    private Long asLongObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return Objects.equals(String.valueOf(value), "1") || Boolean.parseBoolean(String.valueOf(value));
    }

    private long resolveNextCursor(Map<String, Object> data, long currentCursor) {
        long nextCursor = asLong(data.get("next_cursor"), -1L);
        if (nextCursor >= 0) {
            return nextCursor;
        }
        nextCursor = asLong(data.get("cursor"), -1L);
        if (nextCursor >= 0) {
            return nextCursor;
        }
        nextCursor = asLong(data.get("page"), -1L);
        if (nextCursor >= 1) {
            return nextCursor;
        }
        return currentCursor + 1;
    }

    private boolean resolveHasMore(
            Map<String, Object> data,
            List<Map<String, Object>> orders,
            int count,
            long currentCursor,
            long nextCursor) {
        if (data.containsKey("has_more")) {
            return asBoolean(data.get("has_more"));
        }
        if (data.containsKey("more")) {
            return asBoolean(data.get("more"));
        }
        if (data.containsKey("is_has_more")) {
            return asBoolean(data.get("is_has_more"));
        }
        if (data.containsKey("next_cursor")) {
            return StringUtils.hasText(asString(data.get("next_cursor")));
        }
        if (data.containsKey("total") && data.containsKey("page")) {
            long total = asLong(data.get("total"), -1L);
            long page = asLong(data.get("page"), 1L);
            if (total >= 0 && count > 0) {
                return page * (long) count < total;
            }
        }
        if (nextCursor > currentCursor) {
            return !orders.isEmpty();
        }
        return orders.size() >= count;
    }

    private LocalDateTime parseRequiredDateTime(Object value) {
        if (value instanceof Number number) {
            long timestamp = number.longValue();
            if (timestamp > 1_000_000_000_000L) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            }
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
        }
        if (value instanceof CharSequence text) {
            String raw = text.toString().trim();
            if (!StringUtils.hasText(raw)) {
                throw new com.colonel.saas.common.exception.BusinessException("Order create_time is empty and cannot be persisted");
            }
            if (raw.matches("^\\d{10,13}$")) {
                long timestamp = Long.parseLong(raw);
                if (timestamp > 1_000_000_000_000L) {
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
                }
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
            }
            try {
                return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException ignore) {
                // continue trying compatible formats
            }
            try {
                return LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME);
            } catch (DateTimeParseException ignore) {
                // continue trying compatible formats
            }
            try {
                return LocalDateTime.parse(raw + " 00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException ignore) {
                throw new com.colonel.saas.common.exception.BusinessException("Order create_time format is invalid");
            }
        }
        throw new com.colonel.saas.common.exception.BusinessException("Order create_time format is invalid");
    }

    private LocalDateTime parseOptionalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return parseRequiredDateTime(value);
        } catch (BusinessException ex) {
            return null;
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
            boolean locked) {

        public SyncResult(long startTime, long endTime, int pages, int inserted, int skipped, boolean locked) {
            this(startTime, endTime, pages, inserted + skipped, inserted, 0, 0, skipped, locked);
        }

        public int inserted() {
            return created;
        }

        public int skipped() {
            return unattributed;
        }
    }
}

