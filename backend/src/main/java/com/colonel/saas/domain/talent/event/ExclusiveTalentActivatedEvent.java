package com.colonel.saas.domain.talent.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 独家达人协议生效事件（DDD-TALENT-004）。
 *
 * <p>由 {@code ExclusiveTalentApplicationService} 在评估完成、写入
 * effective_talent 表 status=1 时发布；下游业绩域订阅后将该达人所有订单
 * 渠道归因切换到独家持有人。</p>
 */
public record ExclusiveTalentActivatedEvent(
        UUID talentId,
        String talentUid,
        UUID channelUserId,
        UUID deptId,
        String effectiveMonth,
        Long serviceFee,
        Long channelTotalFee,
        java.math.BigDecimal serviceFeeRatio,
        Integer monthlySamples,
        LocalDateTime occurredAt) {
}