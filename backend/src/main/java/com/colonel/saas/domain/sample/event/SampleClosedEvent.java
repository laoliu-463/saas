package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 寄样关闭事件。
 *
 * <p>当寄样请求因超时、取消等原因关闭时触发，记录关闭原因和时间。
 * 通过 {@link SampleDomainEventPublisher#publishSampleClosed} 发布。</p>
 */
public record SampleClosedEvent(
        /** 寄样请求 ID（聚合根 ID）。 */
        UUID sampleRequestId,
        /** 关闭原因（如超时未发货、取消申请等）。 */
        String closeReason,
        /** 关闭时间。 */
        LocalDateTime closedAt) {
}
