package com.colonel.saas.service.talent.profile;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class TalentProfileResult {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_PARTIAL_SUCCESS = "partial_success";
    public static final String STATUS_FAILED = "failed";

    public static final List<String> DEFAULT_UNSUPPORTED = List.of("talentLevel", "sales30d");

    private boolean success;
    private String providerCode;
    private String syncStatus;
    private String errorCode;
    private String errorMessage;
    private String douyinAccount;
    private String talentUid;
    private String secUid;
    private String nickname;
    private String avatarUrl;
    private Long fansCount;
    private Long likeCount;
    private Long followingCount;
    private Long worksCount;
    private String ipLocation;
    private String talentLevel;
    private Long sales30d;
    @Builder.Default
    private List<String> fetchedFields = new ArrayList<>();
    @Builder.Default
    private List<String> unsupportedFields = new ArrayList<>(DEFAULT_UNSUPPORTED);
    @Builder.Default
    private Map<String, Object> rawPayload = new LinkedHashMap<>();

    public boolean hasRealProfileData() {
        return success && (
                isPresent(nickname)
                        || fansCount != null
                        || likeCount != null
                        || worksCount != null
                        || isPresent(avatarUrl));
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
