package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 寄样审批拒绝事件。
 *
 * <p>当寄样请求被审批拒绝时触发，记录拒绝人、拒绝原因和拒绝时间。
 * 通过 {@link SampleDomainEventPublisher#publishSampleRejected} 发布。</p>
 */
public record SampleRejectedEvent(
        /** 寄样请求 ID（聚合根 ID）。 */
        UUID sampleRequestId,
        /** 拒绝人 ID。 */
        UUID rejectedBy,
        /** 拒绝原因。 */
        String rejectedReason,
        /** 拒绝时间。 */
        LocalDateTime rejectedAt) {
}
