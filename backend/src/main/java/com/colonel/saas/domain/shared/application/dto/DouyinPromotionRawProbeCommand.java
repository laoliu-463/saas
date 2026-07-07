package com.colonel.saas.domain.shared.application.dto;

import java.util.Map;

/**
 * 抖音推广 RAW 联调探针命令。
 */
public record DouyinPromotionRawProbeCommand(
        String appId,
        String method,
        Map<String, Object> payload
) {
}
