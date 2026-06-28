package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.order.application.OrderSyncService;
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

/**
 * 抖音 Webhook 事件服务，负责捕获、去重、存储和消费抖音推送的 Webhook 事件。
 *
 * <p>核心逻辑：接收抖音 Webhook 原始报文，解析事件类型与事件键，通过 eventKey 去重
 * （优先从报文中的 event_id / eventId / msg_id 等字段提取，无显式 ID 时对报文取 SHA-256 兜底），
 * 去重后写入 douyin_webhook_events 表并尝试消费。</p>
 *
 * <ul>
 *   <li>提供 {@link #captureColonelOpenEvent} 捕获并去重单条 Webhook 事件，支持 DuplicateKeyException 并发兜底</li>
 *   <li>提供 {@link #replayUnfinished} 重放未完成（RECEIVED / FAILED）的事件</li>
 *   <li>事件生命周期：RECEIVED → CONSUMED / IGNORED / FAILED</li>
 *   <li>消费逻辑仅处理 colonelOpenEvent 类型，提取订单 ID 列表后调用订单同步服务</li>
 * </ul>
 *
 * <p><b>业务领域：</b>订单域 — Webhook 事件捕获与消费</p>
 * <p><b>协作关系：</b>依赖 {@link DouyinWebhookEventMapper} 持久化事件记录；
 * 依赖 {@link OrderSyncService} 对 colonelOpenEvent 提取的订单 ID 执行订单同步；
 * 依赖 {@link ObjectMapper} 解析 JSON 报文</p>
 *
 * @see DouyinWebhookEventMapper
 * @see OrderSyncService
 */
@Slf4j
@Service
public class DouyinWebhookEventService {

    /** 事件状态：已接收，等待消费 */
    public static final String STATUS_RECEIVED = "RECEIVED";

    /** 事件状态：已消费成功 */
    public static final String STATUS_CONSUMED = "CONSUMED";

    /** 事件状态：已忽略（不支持的事件类型或无效 JSON） */
    public static final String STATUS_IGNORED = "IGNORED";

    /** 事件状态：消费失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 抖音团长开放事件类型常量 */
    private static final String COLONEL_OPEN_EVENT = "doudian_alliance_colonelOpenEvent";
    /** Jackson 类型引用，用于反序列化 JSON 为 Map<String, Object> */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    /** Webhook 事件 Mapper，操作 douyin_webhook_events 表 */
    private final DouyinWebhookEventMapper eventMapper;

    /** Jackson 对象映射器，解析 Webhook JSON 报文 */
    private final ObjectMapper objectMapper;

    /** 订单同步服务，消费 colonelOpenEvent 时提取订单 ID 并触发同步 */
    private final OrderSyncService orderSyncService;

    public DouyinWebhookEventService(
            DouyinWebhookEventMapper eventMapper,
            ObjectMapper objectMapper,
            OrderSyncService orderSyncService) {
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper;
        this.orderSyncService = orderSyncService;
    }

    /**
     * 捕获并去重单条 Webhook 事件，写入数据库后立即尝试消费。
     *
     * <ol>
     *   <li>第一步：解析原始报文，提取事件类型和事件键（eventKey）</li>
     *   <li>第二步：通过 eventKey 查询是否已存在，存在则直接返回重复结果</li>
     *   <li>第三步：构建事件实体并插入数据库（RECEIVED 状态）</li>
     *   <li>第四步：若插入时抛出 DuplicateKeyException（并发场景），再次查询并返回已有记录</li>
     *   <li>第五步：插入成功后立即调用消费逻辑处理事件</li>
     * </ol>
     *
     * @param rawBody 抖音 Webhook 推送的原始 JSON 报文
     * @return 捕获结果，包含事件 ID、事件类型、状态和是否为重复事件
     */
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

    /**
     * 重放未完成的 Webhook 事件（状态为 RECEIVED 或 FAILED），逐条重新消费。
     *
     * @param limit 最大重放数量，上限 100
     * @return 重放结果，包含扫描数、消费成功数和失败数
     */
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

    /**
     * 消费单条 Webhook 事件：解析报文，仅处理 colonelOpenEvent 类型，提取订单 ID 并触发同步。
     *
     * <ol>
     *   <li>第一步：重新解析事件原始报文，若 JSON 无效则标记为 IGNORED</li>
     *   <li>第二步：判断事件类型是否为 colonelOpenEvent，非此类型标记为 IGNORED</li>
     *   <li>第三步：从 payload 中提取订单 ID 列表，为空则直接标记为 CONSUMED</li>
     *   <li>第四步：调用订单同步服务同步订单，将同步结果写入 consumeResult</li>
     * </ol>
     */
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

    /**
     * 标记事件处理结果：更新状态、消费结果、处理时间和重试计数，并持久化。
     */
    private void mark(DouyinWebhookEvent event, String status, String consumeResult) {
        event.setStatus(status);
        event.setConsumeResult(consumeResult);
        event.setProcessedAt(LocalDateTime.now());
        event.setRetryCount((event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1);
        eventMapper.updateById(event);
    }

    /**
     * 根据 eventKey 查询已存在的未删除事件记录，用于去重判断。
     */
    private DouyinWebhookEvent findByEventKey(String eventKey) {
        return eventMapper.selectOne(new LambdaQueryWrapper<DouyinWebhookEvent>()
                .eq(DouyinWebhookEvent::getEventKey, eventKey)
                .eq(DouyinWebhookEvent::getDeleted, 0)
                .last("limit 1"));
    }

    /**
     * 解析 Webhook 原始报文为结构化负载，解析失败时返回 validJson=false 的空 payload。
     */
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

    /**
     * 构建事件唯一键：优先从 payload 中提取显式 ID（event_id / eventId / msg_id 等），无显式 ID 时对报文取 SHA-256 兜底。
     * 返回格式为 "{eventType}:{id}"。
     */
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

    /**
     * 从 payload 中提取订单 ID 列表，支持顶层和 data 嵌套中的多种字段名（order_id / orderId / order_ids / orderIds），
     * 使用 LinkedHashSet 去重并保持插入顺序。
     */
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

    /**
     * 递归解析原始值中的订单 ID：支持 List 递归展开、逗号分隔字符串拆分，最终将非空白字符串加入目标集合。
     */
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

    /**
     * 将订单同步结果格式化为可读字符串，包含 fetched / created / updated / failed 计数。
     */
    private String buildSyncConsumeResult(OrderSyncService.SyncResult result) {
        List<String> parts = new ArrayList<>();
        parts.add("fetched=" + result.totalFetched());
        parts.add("created=" + result.created());
        parts.add("updated=" + result.updated());
        parts.add("failed=" + result.failed());
        return "COLONEL_OPEN_EVENT_SYNCED:" + String.join(",", parts);
    }

    /**
     * 从嵌套 Map 中安全提取字符串值，value 非 Map 类型时返回 null。
     */
    @SuppressWarnings("unchecked")
    private String nestedString(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            Object raw = map.get(key);
            return raw == null ? null : String.valueOf(raw);
        }
        return null;
    }

    /**
     * 安全提取事件名称：从 payload 的 event 字段读取，超过 128 字符或包含非法字符时返回 "unknown"。
     */
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

    /** null 安全的 Object 转 String，null 输入返回 null。 */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 返回可变参数中第一个非空白字符串，全部为空时返回 null。 */
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

    /**
     * 对报文内容计算 SHA-256 哈希值，失败时回退为 lowercase hashCode 的十六进制表示。
     */
    private String sha256(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(body.toLowerCase(Locale.ROOT).hashCode());
        }
    }

    /** 解析后的 Webhook 负载 */
    private record ParsedPayload(boolean validJson, String eventType, Map<String, Object> payload) {
    }

    /**
     * Webhook 事件捕获结果。
     *
     * @param eventId   事件 ID
     * @param eventType 事件类型
     * @param status    当前事件状态
     * @param duplicate 是否为重复事件
     */
    public record CaptureResult(UUID eventId, String eventType, String status, boolean duplicate) {
    }

    /**
     * Webhook 事件重放结果。
     *
     * @param scanned  扫描事件总数
     * @param consumed 消费成功数
     * @param failed   消费失败数
     */
    public record ReplayResult(int scanned, int consumed, int failed) {
    }
}
