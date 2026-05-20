package com.colonel.saas.dto.talent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TalentProfilePayload {

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
}
