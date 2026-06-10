package com.colonel.saas.domain.product.policy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 同一 {@code productId} 下一条 activity_product 关联记录的展示策略输入（DDD-PRODUCT-002）。
 */
public record ProductDisplayRelationInput(
        UUID relationId,
        String productId,
        String activityId,
        /** 系统业务状态，如 {@code APPROVED} / {@code PENDING_AUDIT} */
        String systemStatus,
        /** 抖音上游状态：1=推广中 */
        Integer douyinStatus,
        LocalDateTime activityStartTime,
        LocalDateTime activityEndTime,
        /** 当前展示状态：DISPLAYING / HIDDEN / PENDING */
        String displayStatus,
        LocalDateTime listedAt,
        LocalDateTime firstDisplayedAt,
        LocalDateTime lastDisplayedAt,
        BigDecimal commissionRate,
        BigDecimal serviceFeeRate,
        boolean hasTrafficSupport,
        boolean pinned,
        LocalDateTime pinnedUntil,
        UUID actualOwnerId,
        UUID defaultOwnerId,
        boolean selectedToLibrary,
        boolean localPaused,
        boolean localRejected,
        boolean forceDisplay,
        LocalDateTime forceDisplayUntil,
        /** 用于优先级比较的上架/入选时间 */
        LocalDateTime shelfTime,
        Integer protectionMonths
) {
}
