package com.colonel.saas.vo;

import java.time.LocalDateTime;

public record PinnedProductVO(
        String activityId,
        String productId,
        String productName,
        String cover,
        LocalDateTime pinnedAt,
        LocalDateTime pinnedUntil) {
}
