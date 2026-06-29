package com.colonel.saas.domain.product.facade.dto;

import java.time.LocalDateTime;

/**
 * 订单列表展示所需的商品快照只读模型。
 */
public record ProductSnapshotOrderDisplayDTO(
        String activityId,
        String productId,
        String title,
        String cover,
        String shopName,
        Long activityCosRatio,
        String activityCosRatioText,
        String adServiceRatio,
        Long activityAdCosRatio,
        LocalDateTime syncTime) {
}
