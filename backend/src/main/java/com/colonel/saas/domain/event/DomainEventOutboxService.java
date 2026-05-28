package com.colonel.saas.domain.event;

import com.colonel.saas.config.ConfigChangedEventFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 领域事件 Outbox 服务，封装 Outbox 表的业务操作。
 *
 * <p>职责包括：
 * <ul>
 *   <li>将配置变更事件（{@link ConfigChangedEventPayload}）写入 Outbox 表</li>
 *   <li>批量锁定待处理事件供派发器消费</li>
 *   <li>标记事件为已发布或已失败（含指数退避计算）</li>
 *   <li>支持死信事件的人工重放</li>
 *   <li>提供事件分页查询（用于管理后台展示）</li>
 * </ul>
 *
 * <p>配合 {@link DomainEventOutboxMapper} 完成数据库操作，
 * 退避策略采用 5 * 2^min(retry, 6) 秒的指数退避。</p>
 */
@Service
public class DomainEventOutboxService {

    /** 消费成功标识，记录到 consume_log 表。 */
    public static final String CONSUME_SUCCESS = "SUCCESS";

    /** 消费失败标识，记录到 consume_log 表。 */
    public static final String CONSUME_FAILED = "FAILED";

    private final DomainEventOutboxMapper domainEventOutboxMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，注入 Outbox Mapper 和 JSON 序列化器。
     *
     * @param domainEventOutboxMapper Outbox 表数据访问接口
     * @param objectMapper            Jackson JSON 序列化器，用于载荷序列化
     */
    public DomainEventOutboxService(
            DomainEventOutboxMapper domainEventOutboxMapper,
            ObjectMapper objectMapper) {
        this.domainEventOutboxMapper = domainEventOutboxMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存配置变更事件到 Outbox 表，与业务操作在同一事务中提交。
     *
     * <p>事件 ID 取自 payload 自带的 eventId，aggregateId 取第一个配置项的 configKey。</p>
     *
     * @param payload    配置变更事件载荷，包含变更项列表和影响评估
     * @param operatorId 触发变更的操作人 ID（可为 null）
     */
    public void saveConfigChangedEvent(ConfigChangedEventPayload payload, UUID operatorId) {
        DomainEventOutbox outbox = new DomainEventOutbox();
        outbox.setEventId(payload.eventId());
        outbox.setEventType(ConfigChangedEventPayload.EVENT_TYPE);
        outbox.setAggregateType(ConfigChangedEventFactory.AGGREGATE_TYPE);
        outbox.setAggregateId(payload.items().isEmpty() ? null : payload.items().get(0).configKey());
        outbox.setEventVersion(payload.eventVersion());
        outbox.setPayload(serialize(payload));
        outbox.setStatus(DomainEventStatus.PENDING.name());
        outbox.setRetryCount(0);
        outbox.setOccurredAt(payload.changedAt());
        outbox.setCreatedBy(operatorId == null ? null : operatorId.toString());
        domainEventOutboxMapper.insert(outbox);
    }

    /**
     * 批量锁定待处理事件，委托给 Mapper 层执行 SELECT ... FOR UPDATE SKIP LOCKED。
     *
     * @param maxRetry 最大重试次数（表字段为 NULL 时的回退默认值）
     * @param limit    本次最多拉取的事件条数
     * @return 已锁定的待处理事件列表
     */
    public List<DomainEventOutbox> lockPendingEvents(int maxRetry, int limit) {
        return domainEventOutboxMapper.lockPendingEvents(maxRetry, limit);
    }

    /**
     * 标记事件为已发布（PUBLISHED），同时记录发布时间。
     *
     * @param eventId     事件唯一标识
     * @param retryCount  本次投递时的重试次数（回写以保持一致性）
     */
    public void markPublished(UUID eventId, int retryCount) {
        domainEventOutboxMapper.updateDispatchResult(
                eventId,
                DomainEventStatus.PUBLISHED.name(),
                retryCount,
                null,
                LocalDateTime.now(),
                null);
    }

    /**
     * 标记事件投递失败，计算下次重试时间。
     *
     * <p>当 retryCount >= maxRetry 时，事件状态置为 DEAD（不再自动重试）；
     * 否则置为 FAILED，并按指数退避公式 {@code 5 * 2^min(retry, 6)} 秒计算 nextRetryAt。</p>
     *
     * <p>错误信息会被截断至 2000 字符，防止超长异常栈导致存储膨胀。</p>
     *
     * @param eventId      事件唯一标识
     * @param retryCount   当前重试次数（失败后 +1 的值）
     * @param errorMessage 投递失败的错误信息
     * @param maxRetry     最大重试次数阈值，超过则转入 DEAD
     */
    public void markFailed(UUID eventId, int retryCount, String errorMessage, int maxRetry) {
        String status = retryCount >= maxRetry
                ? DomainEventStatus.DEAD.name()
                : DomainEventStatus.FAILED.name();
        LocalDateTime nextRetry = retryCount >= maxRetry
                ? null
                : LocalDateTime.now().plusSeconds((long) Math.pow(2, Math.min(retryCount, 6)) * 5L);
        domainEventOutboxMapper.updateDispatchResult(
                eventId,
                status,
                retryCount,
                truncate(errorMessage, 2000),
                null,
                nextRetry);
    }

    /**
     * 标记事件投递失败（使用默认最大重试次数 5）。
     *
     * @param eventId      事件唯一标识
     * @param retryCount   当前重试次数
     * @param errorMessage 投递失败的错误信息
     */
    public void markFailed(UUID eventId, int retryCount, String errorMessage) {
        markFailed(eventId, retryCount, errorMessage, 5);
    }

    /**
     * 将已死亡（DEAD）的事件重置为待处理（PENDING），用于运维手动重放。
     *
     * @param eventId 事件唯一标识
     */
    public void retryDeadEvent(UUID eventId) {
        domainEventOutboxMapper.resetToPending(eventId);
    }

    /**
     * 分页查询 Outbox 事件，用于管理后台展示。
     *
     * <p>按发生时间（occurredAt）降序排列，支持按状态过滤。</p>
     *
     * @param status 事件状态过滤条件（可为 null 或空字符串，表示不过滤）
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @return 分页后的事件列表
     */
    public List<DomainEventOutbox> pageEvents(String status, long page, long size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DomainEventOutbox> pager =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DomainEventOutbox> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DomainEventOutbox>()
                        .orderByDesc(DomainEventOutbox::getOccurredAt);
        if (status != null && !status.isBlank()) {
            wrapper.eq(DomainEventOutbox::getStatus, status.trim());
        }
        return domainEventOutboxMapper.selectPage(pager, wrapper).getRecords();
    }

    /**
     * 根据事件 ID 查询单个 Outbox 事件。
     *
     * @param eventId 事件唯一标识
     * @return Outbox 事件实体，不存在时返回 null
     */
    public DomainEventOutbox findById(UUID eventId) {
        return domainEventOutboxMapper.selectById(eventId);
    }

    /**
     * 将配置变更事件载荷序列化为 JSON 字符串。
     *
     * @param payload 配置变更事件载荷对象
     * @return JSON 字符串
     * @throws IllegalStateException 当序列化失败时抛出
     */
    private String serialize(ConfigChangedEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize config changed event", ex);
        }
    }

    /**
     * 截断字符串至指定最大长度，用于防止超长错误信息导致存储膨胀。
     *
     * @param message   原始字符串（可为 null）
     * @param maxLength 最大允许长度
     * @return 截断后的字符串，原始长度未超过限制则原样返回
     */
    private String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
