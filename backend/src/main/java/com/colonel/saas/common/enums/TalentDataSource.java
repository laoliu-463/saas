package com.colonel.saas.common.enums;

/**
 * 达人信息来源枚举（合规优先级）：
 * OFFICIAL_API > MANUAL > INTERNAL_BUSINESS > THIRD_PARTY > PUBLIC_PAGE
 */
public enum TalentDataSource {
    MOCK,
    OFFICIAL_API,
    MANUAL,
    INTERNAL_BUSINESS,
    THIRD_PARTY,
    PUBLIC_PAGE
}
