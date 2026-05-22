package com.colonel.saas.vo;

import com.colonel.saas.entity.Talent;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TalentVO {
    private String id;
    private String douyinUid;
    private String douyinNo;
    private String uid;
    private String nickname;
    private Long fansCount;
    private String level;
    private String avatarUrl;
    private String categories;
    private String remark;
    private Long likesCount;
    private Long followingCount;
    private Long worksCount;
    private String ipLocation;
    private Boolean blacklisted;
    private String blacklistReason;
    private Integer status;
    private Long monthlySales;
    private String poolStatus;
    private String ownerId;
    private String ownerName;
    private LocalDateTime claimedAt;
    private LocalDateTime protectedUntil;
    private Integer activeClaimCount;
    private Long sampleCount;
    private Long orderCount;
    private Long serviceFeeContribution;
    private Boolean naturalOrderTalent;
    private String mainCategory;
    private String liveSalesBand;
    private String liveViewBand;
    private String liveGpmBand;
    private String videoSalesBand;
    private String videoPlayBand;
    private String videoGpmBand;

    public static TalentVO from(Talent talent) {
        if (talent == null) {
            return null;
        }
        TalentVO vo = new TalentVO();
        vo.setId(talent.getId() == null ? null : talent.getId().toString());
        vo.setDouyinUid(talent.getDouyinUid());
        vo.setDouyinNo(talent.getDouyinNo());
        vo.setUid(talent.getUid());
        vo.setNickname(talent.getNickname());
        vo.setFansCount(talent.getFans());
        vo.setLevel(talent.getLevel());
        vo.setAvatarUrl(talent.getAvatarUrl());
        vo.setCategories(talent.getCategories());
        vo.setRemark(talent.getIntro());
        vo.setLikesCount(talent.getLikesCount());
        vo.setFollowingCount(talent.getFollowingCount());
        vo.setWorksCount(talent.getWorksCount());
        vo.setIpLocation(talent.getIpLocation());
        vo.setBlacklisted(Boolean.TRUE.equals(talent.getBlacklisted()));
        vo.setBlacklistReason(talent.getBlacklistReason());
        vo.setStatus(talent.getStatus());
        vo.setMonthlySales(talent.getMonthlySales());
        vo.setPoolStatus(talent.getPoolStatus());
        vo.setOwnerId(talent.getOwnerId() == null ? null : talent.getOwnerId().toString());
        vo.setOwnerName(talent.getOwnerName());
        vo.setClaimedAt(talent.getClaimedAt());
        vo.setProtectedUntil(talent.getProtectedUntil());
        vo.setActiveClaimCount(talent.getActiveClaimCount());
        vo.setSampleCount(talent.getSampleCount());
        vo.setOrderCount(talent.getOrderCount());
        vo.setServiceFeeContribution(talent.getServiceFeeContribution());
        vo.setNaturalOrderTalent(talent.getNaturalOrderTalent());
        vo.setMainCategory(talent.getMainCategory());
        vo.setLiveSalesBand(talent.getLiveSalesBand());
        vo.setLiveViewBand(talent.getLiveViewBand());
        vo.setLiveGpmBand(talent.getLiveGpmBand());
        vo.setVideoSalesBand(talent.getVideoSalesBand());
        vo.setVideoPlayBand(talent.getVideoPlayBand());
        vo.setVideoGpmBand(talent.getVideoGpmBand());
        return vo;
    }
}
