package com.colonel.saas.domain.product.facade.dto;

import java.util.UUID;

/**
 * 寄样申请所需的活动商品快照 DTO。
 */
public record ActivityProductForSampleDTO(
        UUID relationId,
        String activityId,
        String productId,
        String title,
        String cover,
        Long price,
        String priceText,
        Long shopId,
        String shopName,
        String detailUrl,
        String promotionStartTime,
        String promotionEndTime,
        String displayStatus,
        Boolean selectedToLibrary,
        boolean visibleForSample,
        UUID ownerUserId
) {
}
