package com.colonel.saas.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 配置变更事件路由器，负责将配置变更事件分发到所有匹配的消费者。
 *
 * <p>分发流程：
 * <ol>
 *   <li>遍历所有已注册的 {@link ConfigChangedEventConsumer} 实现</li>
 *   <li>通过 {@code supports()} 过滤出关心此事件的消费者</li>
 *   <li>通过消费日志检查幂等性，跳过已成功消费过的消费者</li>
 *   <li>调用 {@code consume()} 执行消费逻辑</li>
 *   <li>记录消费结果日志（成功/失败）</li>
 * </ol>
 *
 * <p>整个分发过程在同一事务中执行（{@code @Transactional}），
 * 若任一消费者失败，消费日志会记录失败状态，
 * 但不影响其他消费者的执行；所有消费者执行完毕后若存在失败则抛出最后一个异常。</p>
 */
@Slf4j
@Service
public class ConfigChangedEventRouter {

    /** 所有已注册的配置变更消费者（由 Spring 自动注入）。 */
    private final List<ConfigChangedEventConsumer> consumers;

    /** 消费日志 Mapper，用于幂等性检查和结果记录。 */
    private final DomainEventConsumeLogMapper consumeLogMapper;

    /** JSON 序列化器，传递给消费者使用。 */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，注入消费者列表、消费日志 Mapper 和 JSON 序列化器。
     *
     * @param consumers       所有 {@link ConfigChangedEventConsumer} 实现（Spring List 注入）
     * @param consumeLogMapper 消费日志数据访问接口
     * @param objectMapper    Jackson JSON 序列化器
     */
    public ConfigChangedEventRouter(
            List<ConfigChangedEventConsumer> consumers,
            DomainEventConsumeLogMapper consumeLogMapper,
            ObjectMapper objectMapper) {
        this.consumers = consumers;
        this.consumeLogMapper = consumeLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 分发配置变更事件到所有匹配的消费者。
     *
     * <p>在同一数据库事务中执行所有消费者的消费逻辑，
     * 保证消费日志与业务状态变更的一致性。</p>
     *
     * <p>错误处理策略：收集所有失败但不立即中断，
     * 全部消费者执行完毕后若存在失败则抛出最后一个异常。
     * 这保证了即使某个消费者失败，其他消费者仍有机会执行。</p>
     *
     * @param payload 配置变更事件载荷
     * @throws Exception 当任一消费者执行失败时抛出（抛出的是最后一个失败的异常）
     */
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(ConfigChangedEventPayload payload) throws Exception {
        Exception lastError = null;
        for (ConfigChangedEventConsumer consumer : consumers) {
            if (!consumer.supports(payload)) {
                continue;
            }
            if (alreadyConsumed(payload.eventId(), consumer.consumerName())) {
                continue;
            }
            try {
                consumer.consume(payload, objectMapper);
                saveConsumeLog(payload.eventId(), consumer.consumerName(), DomainEventOutboxService.CONSUME_SUCCESS, null);
            } catch (Exception ex) {
                saveConsumeLog(payload.eventId(), consumer.consumerName(), DomainEventOutboxService.CONSUME_FAILED, ex.getMessage());
                lastError = ex;
                log.warn("ConfigChanged consumer failed, eventId={}, consumer={}",
                        payload.eventId(), consumer.consumerName(), ex);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    /**
     * 检查指定消费者是否已成功消费过该事件（幂等性判断）。
     *
     * @param eventId      事件唯一标识
     * @param consumerName 消费者名称
     * @return true 表示已成功消费过，应跳过
     */
    private boolean alreadyConsumed(UUID eventId, String consumerName) {
        return consumeLogMapper.findSuccessful(eventId, consumerName).isPresent();
    }

    /**
     * 保存消费日志记录，用于幂等性检查和消费结果审计。
     *
     * @param eventId      事件唯一标识
     * @param consumerName 消费者名称
     * @param status       消费结果状态（SUCCESS 或 FAILED）
     * @param errorMessage 失败时的错误信息，成功时为 null
     */
    private void saveConsumeLog(UUID eventId, String consumerName, String status, String errorMessage) {
        DomainEventConsumeLog logEntry = new DomainEventConsumeLog();
        logEntry.setId(UUID.randomUUID());
        logEntry.setEventId(eventId);
        logEntry.setConsumerName(consumerName);
        logEntry.setStatus(status);
        logEntry.setErrorMessage(errorMessage);
        logEntry.setConsumedAt(LocalDateTime.now());
        logEntry.setCreatedAt(LocalDateTime.now());
        consumeLogMapper.insert(logEntry);
    }
}
