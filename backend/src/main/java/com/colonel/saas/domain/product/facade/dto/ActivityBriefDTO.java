package com.colonel.saas.domain.product.facade.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 活动摘要 DTO。
 */
public record ActivityBriefDTO(
        String activityId,
        String activityName,
        Long shopId,
        String shopName,
        Long colonelBuyinId,
        Integer status,
        Integer activityStatusCode,
        String activityStatusText,
        LocalDateTime startTime,
        LocalDateTime endTime,
        UUID recruiterUserId,
        UUID recruiterDeptId
) {
}
