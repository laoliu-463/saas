package com.colonel.saas.domain.product.facade.dto;

import java.util.UUID;

/**
 * 商品展示状态摘要 DTO。
 */
public record ProductDisplayInfoDTO(
        UUID relationId,
        String activityId,
        String productId,
        Integer upstreamStatus,
        String upstreamStatusText,
        String displayStatus,
        Boolean selectedToLibrary,
        Boolean manualDisabled,
        Integer auditStatus,
        String bizStatus,
        String hiddenReason,
        boolean visibleForSample
) {
}
