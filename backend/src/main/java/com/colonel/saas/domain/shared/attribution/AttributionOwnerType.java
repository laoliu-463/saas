package com.colonel.saas.domain.shared.attribution;

import java.util.Locale;

/** 推广链接创建时固化的业务归属维度。 */
public enum AttributionOwnerType {
    CHANNEL,
    RECRUITER;

    public static AttributionOwnerType parseNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
