package com.colonel.saas.domain.shared.application.dto;

/**
 * 抖音 TokenCreate 探针结果。
 */
public record DouyinTokenCreateProbeResult(
        String grantType,
        String codeState,
        String testShop,
        String shopId,
        boolean authIdPresent,
        String authSubjectType,
        DouyinTokenProbeResponseView response) {
}
