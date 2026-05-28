package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 寄样签收事件。
 *
 * <p>当达人确认签收样品时触发，签收后达人可以开始交作业。
 * 通过 {@link SampleDomainEventPublisher#publishSampleSigned} 发布。</p>
 */
public record SampleSignedEvent(
        /** 寄样请求 ID（聚合根 ID）。 */
        UUID sampleRequestId,
        /** 关联的快递单号（用于物流追踪匹配）。 */
        String trackingNo,
        /** 签收时间。 */
        LocalDateTime signedAt) {
}
