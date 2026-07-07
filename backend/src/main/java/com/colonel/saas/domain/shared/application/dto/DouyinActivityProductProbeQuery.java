package com.colonel.saas.domain.shared.application.dto;

/**
 * 抖音活动商品联调探针查询参数。
 */
public record DouyinActivityProductProbeQuery(
        String appId,
        String activityId,
        Integer count,
        String cursor
) {
}
