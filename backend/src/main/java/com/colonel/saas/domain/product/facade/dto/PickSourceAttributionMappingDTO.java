package com.colonel.saas.domain.product.facade.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 订单归因所需的 pick_source 映射只读模型。
 */
public record PickSourceAttributionMappingDTO(
        UUID userId,
        UUID deptId,
        String activityId,
        String productId,
        String colonelBuyinId,
        String sourceType,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
