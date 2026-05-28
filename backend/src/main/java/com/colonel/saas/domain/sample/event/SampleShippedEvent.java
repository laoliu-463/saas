package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 寄样发货事件。
 *
 * <p>当寄样请求的样品发出时触发，记录物流公司和快递单号。
 * 通过 {@link SampleDomainEventPublisher#publishSampleShipped} 发布。</p>
 */
public record SampleShippedEvent(
        /** 寄样请求 ID（聚合根 ID）。 */
        UUID sampleRequestId,
        /** 物流公司编码。 */
        String logisticsCompany,
        /** 快递单号。 */
        String trackingNo,
        /** 发货人 ID。 */
        UUID shippedBy,
        /** 发货时间。 */
        LocalDateTime shippedAt) {
}
