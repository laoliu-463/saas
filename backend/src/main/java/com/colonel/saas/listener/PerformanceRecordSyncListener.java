package com.colonel.saas.listener;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.PerformanceCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 业绩记录同步事件监听器。
 * <p>
 * 监听订单同步完成事件（{@link OrderSyncedEvent}），异步执行业绩计算并发布
 * 业绩计算完成事件。该监听器是订单域到业绩域的关键桥梁，实现了订单同步后的
 * 自动业绩归集。
 * </p>
 * <p>
 * 处理流程：
 * <ol>
 *   <li>接收订单同步事件，通过 orderId 查询完整订单信息</li>
 *   <li>调用业绩计算服务 upsert 业绩记录（插入或更新）</li>
 *   <li>计算成功后发布 {@link PerformanceCalculatedEvent}，通知下游处理</li>
 * </ol>
 * </p>
 * <p>
 * 注意：使用 {@code @Async} 异步执行，不阻塞订单同步主流程。
 * 单个订单的计算失败不影响其他订单，仅记录警告日志。
 * </p>
 *
 * @see OrderSyncedEvent
 * @see PerformanceCalculatedEvent
 * @see PerformanceCalculationService#upsertFromOrder(ColonelsettlementOrder)
 */
@Slf4j
@Component
public class PerformanceRecordSyncListener {

    /** 结算订单 Mapper，用于查询完整订单信息 */
    private final ColonelsettlementOrderMapper orderMapper;
    /** 业绩计算服务，负责从业务规则计算业绩记录 */
    private final PerformanceCalculationService performanceCalculationService;
    /** Spring 事件发布器，用于发布业绩计算完成事件 */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 构造函数，注入依赖。
     *
     * @param orderMapper 结算订单 Mapper
     * @param performanceCalculationService 业绩计算服务
     * @param eventPublisher Spring 事件发布器
     */
    public PerformanceRecordSyncListener(
            ColonelsettlementOrderMapper orderMapper,
            PerformanceCalculationService performanceCalculationService,
            ApplicationEventPublisher eventPublisher) {
        this.orderMapper = orderMapper;
        this.performanceCalculationService = performanceCalculationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 监听订单同步完成事件，异步计算业绩记录。
     * <p>
     * 从订单 Mapper 中查询完整订单对象，然后调用业绩计算服务进行业绩归集。
     * 计算完成后发布 {@link PerformanceCalculatedEvent}，驱动下游流程
     * （如冲正判断、缓存更新等）。
     * </p>
     *
     * @param event 订单同步完成事件，包含 orderId
     */
    @Async
    @EventListener
    public void onOrderSynced(OrderSyncedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        try {
            // 根据 orderId 查询完整订单信息
            ColonelsettlementOrder order = orderMapper.findByOrderId(event.orderId());
            if (order == null) {
                log.warn("Performance calculation skipped, order not found: {}", event.orderId());
                return;
            }
            // 计算并持久化业绩记录（upsert 模式）
            PerformanceRecord record = performanceCalculationService.upsertFromOrder(order);
            if (record == null) {
                return;
            }
            // 发布业绩计算完成事件，通知下游（如仪表盘缓存刷新、冲正检查等）
            eventPublisher.publishEvent(new PerformanceCalculatedEvent(
                    record.getOrderId(),
                    nvl(record.getEstimateGrossProfit()),
                    nvl(record.getEffectiveGrossProfit()),
                    Boolean.TRUE.equals(record.getReversed())));
        } catch (Exception ex) {
            log.warn("Performance calculation failed, orderId={}", event.orderId(), ex);
        }
    }

    /**
     * 空值安全的 Long 转 long 方法。
     *
     * @param value 可能为 null 的 Long 值
     * @return 非 null 的 long 值，null 时返回 0
     */
    private long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
