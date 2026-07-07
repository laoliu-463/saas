package com.colonel.saas.domain.shared.application.dto;

/**
 * 抖音 TokenCreate 探针命令。
 */
public record DouyinTokenCreateProbeCommand(
        String authorizationCode,
        String grantType,
        String testShop,
        String shopId,
        String authId,
        String authSubjectType) {
}
