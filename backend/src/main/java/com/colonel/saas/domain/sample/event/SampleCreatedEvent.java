package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 寄样请求创建事件。
 *
 * <p>当新的寄样请求创建成功后触发，记录商品、达人、渠道、招募人等完整关联信息。
 * 通过 {@link SampleDomainEventPublisher#publishSampleCreated} 发布。</p>
 */
public record SampleCreatedEvent(
        /** 寄样请求 ID（聚合根 ID）。 */
        UUID sampleRequestId,
        /** 商品 ID。 */
        UUID productId,
        /** 商品名称。 */
        String productName,
        /** 达人 ID。 */
        UUID talentId,
        /** 达人昵称。 */
        String talentName,
        /** 渠道用户 ID。 */
        UUID channelId,
        /** 渠道名称。 */
        String channelName,
        /** 招募人 ID（负责招募该达人的运营人员）。 */
        UUID recruiterId,
        /** 合作方 ID（可为 null）。 */
        String partnerId,
        /** 寄样请求初始状态（对应寄样状态枚举值）。 */
        Integer status,
        /** 创建时间。 */
        LocalDateTime createdAt) {
}
