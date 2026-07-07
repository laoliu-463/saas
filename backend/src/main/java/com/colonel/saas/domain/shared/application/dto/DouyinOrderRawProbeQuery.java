package com.colonel.saas.domain.shared.application.dto;

/**
 * 抖音订单 RAW 联调探针查询参数。
 */
public record DouyinOrderRawProbeQuery(
        long startTime,
        long endTime,
        int count,
        String cursor
) {
}
