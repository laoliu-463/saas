package com.colonel.saas.domain.talent.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 独家达人协议失效事件（DDD-TALENT-004）。
 *
 * <p>当某达人当月不再满足服务费占比或月寄样阈值时（评估结果为不达标），
 * 写入 status=0 后发布；下游业绩域应在下个生效月清掉独家覆盖。</p>
 */
public record ExclusiveTalentExpiredEvent(
        UUID talentId,
        String talentUid,
        String effectiveMonth,
        LocalDateTime occurredAt) {
}