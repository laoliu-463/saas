package com.colonel.saas.constant;

import java.util.Locale;

/**
 * 活动商品关联在商品库中的展示状态。
 */
public enum ProductDisplayStatus {

    PENDING("待定"),
    DISPLAYING("展示中"),
    HIDDEN("已隐藏");

    private final String label;

    ProductDisplayStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ProductDisplayStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return PENDING;
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PENDING;
        }
    }
}
