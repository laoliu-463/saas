package com.colonel.saas.common.enums;

import java.util.Arrays;

public enum TalentFollowStatus {

    NOT_CONTACTED("未联系"),
    INVITED("已邀约"),
    REPLIED("已回复"),
    COOPERATING("已合作"),
    PROMOTING("推广中"),
    CLOSED("已结束");

    private final String label;

    TalentFollowStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static TalentFollowStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown TalentFollowStatus: " + code));
    }
}
