package com.colonel.saas.job;

import com.colonel.saas.domain.event.ConfigChangedEventPayload;
import com.colonel.saas.domain.event.ConfigChangedEventRouter;
import com.colonel.saas.domain.event.DomainEventOutbox;
import com.colonel.saas.domain.event.DomainEventOutboxService;
import com.colonel.saas.domain.event.ProductDomainEventOutboxRouter;
import com.colonel.saas.domain.order.event.OrderDomainEventOutboxRouter;
import com.colonel.saas.domain.sample.event.SampleDomainEventOutboxRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 领域事件分发定时任务（Outbox 模式消费者）。
 * <p>
 * 实现 Outbox 模式的事件投递端：定时从 {@code domain_event_outbox} 表中
 * 拉取待处理的领域事件，按事件类型路由到对应的处理器进行分发。
 * </p>
 * <p>
 * Outbox 模式保证了业务操作和事件写入在同一事务中完成，
 * 再由本任务异步读取并分发，解决分布式环境下的事件最终一致性问题。
 * </p>
 * <p>
 * 支持的事件域：
 * <ul>
 *   <li><b>配置变更事件</b> → {@link ConfigChangedEventRouter}</li>
 *   <li><b>商品域事件</b> → {@link ProductDomainEventOutboxRouter}</li>
 *   <li><b>寄样域事件</b> → {@link SampleDomainEventOutboxRouter}</li>
 *   <li><b>订单域事件</b> → {@link OrderDomainEventOutboxRouter}</li>
 * </ul>
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>固定延迟 5 秒（{@code app.domain-event.dispatch-interval-ms}），上一次完成后等待 5 秒再执行</li>
 *   <li>批次大小：20 条/次，避免单次处理过多事件导致长事务</li>
 *   <li>最大重试次数：3 次，超过后标记为失败</li>
 *   <li>默认启用，可通过 {@code app.domain-event.dispatch-enabled=false} 关闭</li>
 *   <li>在事务内执行（{@code @Transactional}），确保事件状态更新的原子性</li>
 * </ul>
 * </p>
 *
 * @see DomainEventOutboxService
 * @see com.colonel.saas.domain.event.DomainEventOutbox
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.domain-event.dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class DomainEventDispatcherJob {

    /** 事件最大重试次数 */
    private static final int MAX_RETRY = 3;
    /** 每次拉取的事件批次大小 */
    private static final int BATCH_SIZE = 20;

    /** Outbox 事件服务，负责事件的锁定、状态更新等 */
    private final DomainEventOutboxService domainEventOutboxService;
    /** 配置变更事件路由器 */
    private final ConfigChangedEventRouter configChangedEventRouter;
    /** 商品域事件路由器 */
    private final ProductDomainEventOutboxRouter productDomainEventOutboxRouter;
    /** 寄样域事件路由器 */
    private final SampleDomainEventOutboxRouter sampleDomainEventOutboxRouter;
    /** 订单域事件路由器 */
    private final OrderDomainEventOutboxRouter orderDomainEventOutboxRouter;
    /** JSON 序列化器，用于反序列化事件 payload */
    private final ObjectMapper objectMapper;

    public DomainEventDispatcherJob(
            DomainEventOutboxService domainEventOutboxService,
            ConfigChangedEventRouter configChangedEventRouter,
            ProductDomainEventOutboxRouter productDomainEventOutboxRouter,
            SampleDomainEventOutboxRouter sampleDomainEventOutboxRouter,
            OrderDomainEventOutboxRouter orderDomainEventOutboxRouter,
            ObjectMapper objectMapper) {
        this.domainEventOutboxService = domainEventOutboxService;
        this.configChangedEventRouter = configChangedEventRouter;
        this.productDomainEventOutboxRouter = productDomainEventOutboxRouter;
        this.sampleDomainEventOutboxRouter = sampleDomainEventOutboxRouter;
        this.orderDomainEventOutboxRouter = orderDomainEventOutboxRouter;
        this.objectMapper = objectMapper;
    }

    /**
     * 拉取并分发待处理的领域事件。
     * <p>
     * 在同一事务中锁定待处理事件并逐一分发，
     * 确保事件状态（发布成功/失败）与业务逻辑的一致性。
     * </p>
     */
    @Scheduled(fixedDelayString = "${app.domain-event.dispatch-interval-ms:5000}")
    @Transactional(rollbackFor = Exception.class)
    public void dispatchPendingEvents() {
        // 锁定待处理事件（悲观锁，防止多实例并发处理同一批事件）
        List<DomainEventOutbox> pending = domainEventOutboxService.lockPendingEvents(MAX_RETRY, BATCH_SIZE);
        for (DomainEventOutbox event : pending) {
            dispatchOne(event);
        }
    }

    /**
     * 分发单条领域事件。
     * <p>
     * 根据事件类型路由到对应的处理器：
     * <ol>
     *   <li>配置变更事件 → ConfigChangedEventRouter</li>
     *   <li>商品域事件 → ProductDomainEventOutboxRouter</li>
     *   <li>寄样域事件 → SampleDomainEventOutboxRouter</li>
     * </ol>
     * 分发成功后标记为已发布；分发失败后累加重试计数，超过最大重试次数则标记为最终失败。
     * </p>
     *
     * @param event 待分发的领域事件
     */
    private void dispatchOne(DomainEventOutbox event) {
        try {
            // 根据事件类型路由到对应处理器
            if (ConfigChangedEventPayload.EVENT_TYPE.equals(event.getEventType())) {
                ConfigChangedEventPayload payload = objectMapper.readValue(event.getPayload(), ConfigChangedEventPayload.class);
                configChangedEventRouter.dispatch(payload);
            } else if (productDomainEventOutboxRouter.supports(event.getEventType())) {
                productDomainEventOutboxRouter.dispatch(event);
            } else if (sampleDomainEventOutboxRouter.supports(event.getEventType())) {
                sampleDomainEventOutboxRouter.dispatch(event);
            } else if (orderDomainEventOutboxRouter.supports(event.getEventType())) {
                orderDomainEventOutboxRouter.dispatch(event);
            }
            // 分发成功，标记为已发布
            domainEventOutboxService.markPublished(event.getEventId(), event.getRetryCount() == null ? 0 : event.getRetryCount());
        } catch (Exception ex) {
            // 分发失败，累加重试计数
            int retryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
            int maxRetry = event.getMaxRetry() == null ? MAX_RETRY : event.getMaxRetry();
            domainEventOutboxService.markFailed(event.getEventId(), retryCount, ex.getMessage(), maxRetry);
            log.warn("Domain event dispatch failed, eventId={}, retryCount={}", event.getEventId(), retryCount, ex);
        }
    }
}
