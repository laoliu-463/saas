package com.colonel.saas.config;

/**
 * 达人真实采集联调状态（文档口径，禁止虚报 REAL_CONNECTED）。
 */
public enum TalentCollectEnvironmentStatus {
    MOCK_ONLY,
    NOT_CONFIGURED,
    NOT_AUTHORIZED,
    UNSUPPORTED,
    CRAWLER_FALLBACK,
    REAL_CONNECTED
}
