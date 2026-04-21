package com.colonel.saas.service;

import com.colonel.saas.douyin.api.OrderApi;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ColonelsettlementOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private final OrderApi orderApi;
    private final OrderSyncPersistenceService persistenceService;
    private final AttributionService attributionService;
    private final RedisTemplate<String, Object> redisTemplate;

    public OrderSyncService(
            OrderApi orderApi,
            OrderSyncPersistenceService persistenceService,
            AttributionService attributionService,
            RedisTemplate<String, Object> redisTemplate) {
        this.orderApi = orderApi;
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
            throw new BusinessException("非法同步时间范围");
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
            redisTemplate.opsForValue().set(LAST_SYNC_TIME_KEY, endTime);
            return result;
        } finally {
            redisTemplate.delete(SYNC_LOCK_KEY);
        }
    }

    private SyncResult syncRange(long startTime, long endTime, int count) {
        long cursor = 0L;
        boolean hasMore = true;
        int inserted = 0;
        int skipped = 0;
        int pages = 0;
        while (hasMore) {
            Map<String, Object> response = orderApi.listSettlement(startTime, endTime, count, String.valueOf(cursor));
            Map<String, Object> data = getData(response);
            List<Map<String, Object>> orders = getOrderList(data);
            pages++;
            for (Map<String, Object> orderMap : orders) {
                try {
                    ColonelsettlementOrder order = mapOrder(orderMap);
                    if (!StringUtils.hasText(order.getOrderId())) {
                        continue;
                    }
                    AttributionService.AttributionResult attribution = attributionService.resolveAttribution(order, orderMap);
                    order.setChannelUserId(attribution.channelUserId());
                    order.setUserId(attribution.userId());
                    order.setDeptId(attribution.deptId());
                    boolean insertedNow = persistenceService.persistOrder(order);
                    if (insertedNow) {
                        inserted++;
                    } else {
                        skipped++;
                    }
                } catch (BusinessException e) {
                    skipped++;
                    log.warn("Skip order during sync, reason={}, payload={}", e.getMessage(), orderMap);
                }
            }
            cursor = asLong(data.get("cursor"), cursor);
            hasMore = asBoolean(data.get("has_more"));
            if (orders.isEmpty()) {
                break;
            }
            if (pages >= MAX_PAGES) {
                log.warn("Order sync reaches max pages limit, start={}, end={}, pages={}", startTime, endTime, pages);
                break;
            }
        }
        log.info("Order sync completed, range=[{}, {}], pages={}, inserted={}, skipped={}",
                startTime, endTime, pages, inserted, skipped);
        return new SyncResult(startTime, endTime, pages, inserted, skipped, false);
    }

    private Map<String, Object> getData(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> converted = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return converted;
        }
        return Map.of();
    }

    private List<Map<String, Object>> getOrderList(Map<String, Object> data) {
        Object orders = data.get("order_list");
        if (!(orders instanceof List<?>)) {
            orders = data.get("orders");
            if (!(orders instanceof List<?>)) {
                orders = data.get("list");
            }
        }
        if (!(orders instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> {
                    Map<?, ?> raw = (Map<?, ?>) item;
                    Map<String, Object> converted = new HashMap<>();
                    for (Map.Entry<?, ?> entry : raw.entrySet()) {
                        if (entry.getKey() != null) {
                            converted.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                    return converted;
                })
                .toList();
    }

    private ColonelsettlementOrder mapOrder(Map<String, Object> source) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId(asString(source.get("order_id")));
        order.setProductId(asString(source.get("product_id")));
        order.setProductName(firstNonBlank(
                asString(source.get("product_name")),
                asString(source.get("title"))
        ));
        order.setShopId(asLongObject(source.get("shop_id")));
        order.setShopName(asString(source.get("shop_name")));
        order.setOrderAmount(asLong(source.get("order_amount"), 0L));
        order.setActualAmount(asLong(source.get("actual_amount"), asLong(source.get("order_amount"), 0L)));
        order.setSettleColonelCommission(asLong(source.get("settle_colonel_commission"), asLong(source.get("service_fee"), 0L)));
        order.setSettleColonelTechServiceFee(asLong(source.get("settle_colonel_tech_service_fee"), asLong(source.get("platform_fee"), 0L)));
        order.setSettleSecondColonelCommission(asLong(source.get("settle_second_colonel_commission"), asLong(source.get("commission_fee"), 0L)));
        order.setOrderStatus(asInt(source.get("order_status"), asInt(source.get("status"), 1)));
        String pickSource = firstNonBlank(asString(source.get("pick_source")), asString(source.get("pick_extra")));
        if (StringUtils.hasText(pickSource) && pickSource.length() > 128) {
            pickSource = pickSource.substring(0, 128);
        }
        order.setPickSource(pickSource);
        order.setCreateTime(parseRequiredDateTime(source.get("create_time")));
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(0);
        order.setExtraData(source);
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

    private LocalDateTime parseRequiredDateTime(Object value) {
        if (value == null) {
            throw new com.colonel.saas.common.exception.BusinessException("订单 create_time 为空，无法写入分区表");
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof Number number) {
            long timestamp = number.longValue();
            if (timestamp > 1_000_000_000_000L) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            }
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
        }
        throw new com.colonel.saas.common.exception.BusinessException("订单 create_time 非法，无法写入分区表");
    }

    public record SyncResult(long startTime, long endTime, int pages, int inserted, int skipped, boolean locked) {
    }
}
