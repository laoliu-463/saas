package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.DouyinWebhookEvent;
import com.colonel.saas.mapper.DouyinWebhookEventMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DouyinWebhookEventService {

    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_CONSUMED = "CONSUMED";
    public static final String STATUS_IGNORED = "IGNORED";
    public static final String STATUS_FAILED = "FAILED";

    private static final String COLONEL_OPEN_EVENT = "doudian_alliance_colonelOpenEvent";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DouyinWebhookEventMapper eventMapper;
    private final ObjectMapper objectMapper;
    private final OrderSyncService orderSyncService;

    public DouyinWebhookEventService(
            DouyinWebhookEventMapper eventMapper,
            ObjectMapper objectMapper,
            OrderSyncService orderSyncService) {
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper;
        this.orderSyncService = orderSyncService;
    }

    @Transactional(rollbackFor = Exception.class)
    public CaptureResult captureColonelOpenEvent(String rawBody) {
        String body = rawBody == null ? "" : rawBody;
        ParsedPayload parsed = parse(body);
        String eventType = parsed.eventType();
        String eventKey = buildEventKey(eventType, parsed.payload(), body);

        DouyinWebhookEvent existing = findByEventKey(eventKey);
        if (existing != null) {
            return new CaptureResult(existing.getId(), existing.getEventType(), existing.getStatus(), true);
        }

        DouyinWebhookEvent event = new DouyinWebhookEvent();
        event.setId(UUID.randomUUID());
        event.setEventKey(eventKey);
        event.setEventType(eventType);
        event.setPayloadHash(sha256(body));
        event.setBodyLength(body.length());
        event.setRawPayload(body);
        event.setStatus(STATUS_RECEIVED);
        event.setConsumeResult("PENDING");
        event.setRetryCount(0);
        event.setReceivedAt(LocalDateTime.now());
        event.setDeleted(0);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException ex) {
            DouyinWebhookEvent concurrent = findByEventKey(eventKey);
            if (concurrent != null) {
                return new CaptureResult(concurrent.getId(), concurrent.getEventType(), concurrent.getStatus(), true);
            }
            throw ex;
        }

        consume(event);
        return new CaptureResult(event.getId(), event.getEventType(), event.getStatus(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReplayResult replayUnfinished(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<DouyinWebhookEvent> events = eventMapper.selectList(new LambdaQueryWrapper<DouyinWebhookEvent>()
                .in(DouyinWebhookEvent::getStatus, STATUS_RECEIVED, STATUS_FAILED)
                .eq(DouyinWebhookEvent::getDeleted, 0)
                .orderByAsc(DouyinWebhookEvent::getCreateTime)
                .last("limit " + safeLimit));
        int consumed = 0;
        int failed = 0;
        for (DouyinWebhookEvent event : events) {
            consume(event);
            if (STATUS_FAILED.equals(event.getStatus())) {
                failed++;
            } else {
                consumed++;
            }
        }
        return new ReplayResult(events.size(), consumed, failed);
    }

    private void consume(DouyinWebhookEvent event) {
        try {
            ParsedPayload parsed = parse(event.getRawPayload());
            if (!parsed.validJson()) {
                mark(event, STATUS_IGNORED, "INVALID_JSON");
                return;
            }
            if (COLONEL_OPEN_EVENT.equals(parsed.eventType())) {
                List<String> orderIds = extractOrderIds(parsed.payload());
                if (orderIds.isEmpty()) {
                    mark(event, STATUS_CONSUMED, "COLONEL_OPEN_EVENT_CAPTURED");
                    return;
                }
                OrderSyncService.SyncResult result = orderSyncService.syncByOrderIds(orderIds);
                mark(event, STATUS_CONSUMED, buildSyncConsumeResult(result));
                return;
            }
            mark(event, STATUS_IGNORED, "UNSUPPORTED_EVENT");
        } catch (Exception ex) {
            log.warn("Douyin webhook consume failed, eventId={}, eventType={}, exception={}",
                    event.getId(), event.getEventType(), ex.getClass().getSimpleName());
            mark(event, STATUS_FAILED, ex.getClass().getSimpleName());
        }
    }

    private void mark(DouyinWebhookEvent event, String status, String consumeResult) {
        event.setStatus(status);
        event.setConsumeResult(consumeResult);
        event.setProcessedAt(LocalDateTime.now());
        event.setRetryCount((event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1);
        eventMapper.updateById(event);
    }

    private DouyinWebhookEvent findByEventKey(String eventKey) {
        return eventMapper.selectOne(new LambdaQueryWrapper<DouyinWebhookEvent>()
                .eq(DouyinWebhookEvent::getEventKey, eventKey)
                .eq(DouyinWebhookEvent::getDeleted, 0)
                .last("limit 1"));
    }

    private ParsedPayload parse(String body) {
        if (!StringUtils.hasText(body)) {
            return new ParsedPayload(false, "unknown", Map.of());
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);
            return new ParsedPayload(true, safeEventName(payload), payload);
        } catch (Exception ex) {
            return new ParsedPayload(false, "unknown", Map.of());
        }
    }

    private String buildEventKey(String eventType, Map<String, Object> payload, String body) {
        String explicitId = firstNonBlank(
                asString(payload.get("event_id")),
                asString(payload.get("eventId")),
                asString(payload.get("msg_id")),
                asString(payload.get("msgId")),
                asString(payload.get("id")),
                nestedString(payload.get("data"), "event_id"),
                nestedString(payload.get("data"), "eventId"),
                nestedString(payload.get("data"), "order_id"),
                nestedString(payload.get("data"), "orderId")
        );
        if (StringUtils.hasText(explicitId)) {
            return eventType + ":" + explicitId;
        }
        return eventType + ":" + sha256(body);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractOrderIds(Map<String, Object> payload) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addOrderIds(result, payload.get("order_id"));
        addOrderIds(result, payload.get("orderId"));
        addOrderIds(result, payload.get("order_ids"));
        addOrderIds(result, payload.get("orderIds"));
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> nested) {
            Map<String, Object> nestedMap = (Map<String, Object>) nested;
            addOrderIds(result, nestedMap.get("order_id"));
            addOrderIds(result, nestedMap.get("orderId"));
            addOrderIds(result, nestedMap.get("order_ids"));
            addOrderIds(result, nestedMap.get("orderIds"));
        }
        return List.copyOf(result);
    }

    private void addOrderIds(LinkedHashSet<String> target, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        if (rawValue instanceof List<?> list) {
            for (Object item : list) {
                addOrderIds(target, item);
            }
            return;
        }
        String text = String.valueOf(rawValue).trim();
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (text.contains(",")) {
            for (String part : text.split(",")) {
                addOrderIds(target, part);
            }
            return;
        }
        target.add(text);
    }

    private String buildSyncConsumeResult(OrderSyncService.SyncResult result) {
        List<String> parts = new ArrayList<>();
        parts.add("fetched=" + result.totalFetched());
        parts.add("created=" + result.created());
        parts.add("updated=" + result.updated());
        parts.add("failed=" + result.failed());
        return "COLONEL_OPEN_EVENT_SYNCED:" + String.join(",", parts);
    }

    @SuppressWarnings("unchecked")
    private String nestedString(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            Object raw = map.get(key);
            return raw == null ? null : String.valueOf(raw);
        }
        return null;
    }

    private String safeEventName(Map<String, Object> payload) {
        String eventName = asString(payload.get("event"));
        if (!StringUtils.hasText(eventName)) {
            return "unknown";
        }
        String value = eventName.trim();
        if (value.length() > 128 || !value.matches("[A-Za-z0-9._:-]+")) {
            return "unknown";
        }
        return value;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String sha256(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(body.toLowerCase(Locale.ROOT).hashCode());
        }
    }

    private record ParsedPayload(boolean validJson, String eventType, Map<String, Object> payload) {
    }

    public record CaptureResult(UUID eventId, String eventType, String status, boolean duplicate) {
    }

    public record ReplayResult(int scanned, int consumed, int failed) {
    }
}
