package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent")
public class Talent extends BaseEntity {

    @TableField("douyin_uid")
    private String douyinUid;

    @TableField("douyin_no")
    private String douyinNo;

    @TableField("uid")
    private String uid;

    @TableField("sec_uid")
    private String secUid;

    @TableField("profile_url")
    private String profileUrl;

    private String nickname;

    @TableField("fans_count")
    @JsonProperty("fansCount")
    private Long fans;

    @TableField("fans_level")
    private String level;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("intro")
    private String intro;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("contact_wechat")
    private String contactWechat;

    @TableField("likes_count")
    private Long likesCount;

    @TableField("following_count")
    private Long followingCount;

    @TableField("works_count")
    private Long worksCount;

    @TableField("ip_location")
    private String ipLocation;

    @TableField("crawl_status")
    private Integer crawlStatus;

    @TableField("crawl_message")
    private String crawlMessage;

    @TableField("last_crawl_at")
    private LocalDateTime lastCrawlAt;

    @TableField("enrich_status")
    private String enrichStatus;

    @TableField("last_enrich_time")
    private LocalDateTime lastEnrichTime;

    @TableField("data_source")
    private String dataSource;

    private Integer status;

    @TableField(exist = false)
    private Long monthlySales;

    @TableField(exist = false)
    private UUID ownerId;

    @TableField(exist = false)
    private LocalDateTime claimedAt;

    @TableField(exist = false)
    private String poolStatus;

    @TableField(exist = false)
    private String ownerName;

    @TableField(exist = false)
    private LocalDateTime protectedUntil;

    @TableField(exist = false)
    private Long sampleCount;

    @TableField(exist = false)
    private Long orderCount;

    @TableField(exist = false)
    private Long serviceFeeContribution;
}
