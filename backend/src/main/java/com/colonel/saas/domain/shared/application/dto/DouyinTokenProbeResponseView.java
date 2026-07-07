package com.colonel.saas.domain.shared.application.dto;

/**
 * 抖音 TokenCreate 探针响应视图。
 */
public record DouyinTokenProbeResponseView(
        String code,
        String msg,
        String subCode,
        String subMsg,
        String maskedAccessToken,
        String maskedRefreshToken,
        Long expiresIn,
        String authorityId,
        String authSubjectType,
        Long tokenType) {
}
