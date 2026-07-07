package com.colonel.saas.domain.shared.application.dto;

/**
 * Query parameters for Douyin activity diagnostic list probes.
 */
public record DouyinActivityListProbeQuery(
        String appId,
        Integer status,
        Long searchType,
        Long sortType,
        Long page,
        Long pageSize,
        String activityInfo) {
}
