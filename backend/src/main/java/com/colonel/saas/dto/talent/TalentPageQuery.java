package com.colonel.saas.dto.talent;

import com.colonel.saas.common.enums.DataScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.UUID;

@Data
public class TalentPageQuery {

    @Min(1)
    private long page = 1;

    @Min(1)
    @Max(100)
    private long size = 10;

    private String keyword;
    private String douyinNo;
    private String nickname;
    private String platform;
    private String region;
    private String poolStatus;
    private String ownerKeyword;
    private Long minFans;
    private Long maxFans;

    private String view;
    private String category;
    private String claimStatus;
    private String liveSalesBand;
    private String liveViewBand;
    private String liveGpmBand;
    private String videoSalesBand;
    private String videoPlayBand;
    private String videoGpmBand;
    private String level;
    private String gender;
    private String contactStatus;

    private UUID userId;
    private UUID deptId;
    private DataScope dataScope;
}
