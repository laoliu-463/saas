package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 寄样审批通过事件。
 *
 * <p>当寄样请求被审批通过时触发，记录审批人和审批时间。
 * 通过 {@link SampleDomainEventPublisher#publishSampleApproved} 发布。</p>
 */
public record SampleApprovedEvent(
        /** 寄样请求 ID（聚合根 ID）。 */
        UUID sampleRequestId,
        /** 商品 ID。 */
        UUID productId,
        /** 达人 ID。 */
        UUID talentId,
        /** 渠道用户 ID。 */
        UUID channelId,
        /** 招募人 ID。 */
        UUID recruiterId,
        /** 审批人 ID。 */
        UUID approvedBy,
        /** 审批时间。 */
        LocalDateTime approvedAt) {
}
