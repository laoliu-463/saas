package com.colonel.saas.domain.order.policy;

import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;

import java.time.LocalDateTime;
import java.util.UUID;

/** 可审计的推广链接归属解析结果。 */
public record OrderLinkAttributionResolution(
        Status status,
        UUID userId,
        UUID deptId,
        AttributionOwnerType ownerType,
        String source,
        String reason,
        boolean nativeKeyMatched,
        boolean colonelBuyinIdMismatch,
        LocalDateTime mappingCreatedAt) {

    public enum Status {
        UNIQUE,
        NOT_FOUND,
        AMBIGUOUS,
        OWNER_TYPE_MISSING,
        MAPPING_AFTER_ORDER
    }
}
