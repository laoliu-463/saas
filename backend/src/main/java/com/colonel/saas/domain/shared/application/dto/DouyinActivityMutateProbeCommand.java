package com.colonel.saas.domain.shared.application.dto;

/**
 * Command payload for Douyin activity create/update diagnostic probes.
 */
public record DouyinActivityMutateProbeCommand(
        String appId,
        Long activityId,
        Boolean applicationLimited,
        Boolean isNewShop,
        String shopType,
        String activityName,
        String activityDesc,
        String applyStartTime,
        String applyEndTime,
        String commissionRate,
        String serviceRate,
        String wechatId,
        String phoneNum,
        String estimatedSingleSale,
        Integer activityType,
        String specifiedShopIds,
        Boolean online,
        String categories,
        Integer shopScore,
        Integer minPromotionDays,
        Integer thresholdCrossBorder,
        Integer minExclusionDuration,
        String adCommissionRate,
        String adServiceRate,
        Integer cosLimitType) {
}
