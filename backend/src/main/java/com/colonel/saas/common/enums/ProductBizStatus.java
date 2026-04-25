package com.colonel.saas.common.enums;

import java.util.Arrays;

public enum ProductBizStatus {

    SYNCED("已同步"),
    PENDING_AUDIT("待审核"),
    APPROVED("审核通过"),
    REJECTED("审核拒绝"),
    BOUND("已绑定活动"),
    ASSIGNED("已分配招商"),
    LINKED("已转链"),
    FOLLOWING("达人跟进中");

    private final String label;

    ProductBizStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ProductBizStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ProductBizStatus: " + code));
    }
}
