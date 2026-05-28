package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 寄样完成事件（交作业完成）。
 *
 * <p>当达人通过订单已同步事件判断交作业完成时触发。
 * 通过 {@link SampleDomainEventPublisher#publishSampleCompleted} 发布。
 * 该事件将触发业绩域的归属和提成计算。</p>
 */
public record SampleCompletedEvent(
        /** 寄样请求 ID（聚合根 ID）。 */
        UUID sampleRequestId,
        /** 关联的订单 ID（达人交作业产生的带货订单）。 */
        String orderId,
        /** 商品 ID。 */
        UUID productId,
        /** 达人 ID。 */
        UUID talentId,
        /** 渠道用户 ID。 */
        UUID channelId,
        /** 完成时间。 */
        LocalDateTime completedAt) {
}
