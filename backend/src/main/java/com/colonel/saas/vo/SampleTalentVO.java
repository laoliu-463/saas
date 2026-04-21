package com.colonel.saas.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SampleTalentVO {
    private String talentId;
    private String nickname;
    private String avatarUrl;
    private Long fansCount;
    private BigDecimal creditScore;
    private String mainCategory;
    private String region;
}

