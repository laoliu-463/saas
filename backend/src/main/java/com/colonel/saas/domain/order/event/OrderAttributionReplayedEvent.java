package com.colonel.saas.domain.order.event;

import java.util.UUID;

/**
 * 订单归因事实已被受控重放更正。
 *
 * <p>该事件只表达订单事实版本变化，不携带业绩结论；业绩域消费后读取最新订单事实并自行 upsert。</p>
 */
public record OrderAttributionReplayedEvent(
        String orderId,
        UUID orderRowId,
        int orderVersion) {
}
