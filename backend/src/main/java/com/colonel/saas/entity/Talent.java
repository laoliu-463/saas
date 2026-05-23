package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import com.colonel.saas.common.typehandler.JsonbListTypeHandler;
import com.colonel.saas.common.typehandler.JsonbTypeHandler;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "talent", autoResultMap = true)
public class Talent extends VersionedEntity {

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

    @TableField("categories")
    private String categories;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("contact_wechat")
    private String contactWechat;

    @TableField(value = "talent_tags", typeHandler = JsonbListTypeHandler.class)
    private List<String> tags;

    @TableField("tag_updated_by")
    private UUID tagUpdatedBy;

    @TableField("shipping_recipient_name")
    private String shippingRecipientName;

    @TableField("shipping_recipient_phone")
    private String shippingRecipientPhone;

    @TableField("shipping_recipient_address")
    private String shippingRecipientAddress;

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

    @TableField("douyin_account")
    private String douyinAccount;

    @TableField("talent_uid")
    private String talentUid;

    @TableField("talent_level")
    private String talentLevel;

    @TableField("sales_30d")
    private Long sales30d;

    @TableField("sync_status")
    private String syncStatus;

    @TableField("last_sync_time")
    private LocalDateTime lastSyncTime;

    @TableField("sync_error_code")
    private String syncErrorCode;

    @TableField("sync_error_message")
    private String syncErrorMessage;

    @TableField(value = "raw_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawPayload;

    @TableField(value = "unsupported_fields", typeHandler = JsonbListTypeHandler.class)
    private List<String> unsupportedFields;

    @TableField("blacklisted")
    private Boolean blacklisted;

    @TableField("blacklist_reason")
    private String blacklistReason;

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
    private Integer activeClaimCount;

    @TableField(exist = false)
    private Long sampleCount;

    @TableField(exist = false)
    private Long orderCount;

    @TableField(exist = false)
    private Long serviceFeeContribution;

    @TableField(exist = false)
    private Boolean naturalOrderTalent;

    @TableField(exist = false)
    private String mainCategory;

    @TableField(exist = false)
    private String liveSalesBand;

    @TableField(exist = false)
    private String liveViewBand;

    @TableField(exist = false)
    private String liveGpmBand;

    @TableField(exist = false)
    private String videoSalesBand;

    @TableField(exist = false)
    private String videoPlayBand;

    @TableField(exist = false)
    private String videoGpmBand;
}
