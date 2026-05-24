package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("colonel_partner")
public class ColonelPartner extends VersionedEntity {

    @TableField("colonel_buyin_id")
    private String colonelBuyinId;

    @TableField("colonel_name")
    private String colonelName;

    @TableField("contact_name")
    private String contactName;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("contact_wechat")
    private String contactWechat;

    @TableField("contact_remark")
    private String contactRemark;

    private String source;

    @TableField("first_seen_at")
    private LocalDateTime firstSeenAt;

    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    @TableField("manual_contact_updated_at")
    private LocalDateTime manualContactUpdatedAt;

    @TableField("manual_contact_updated_by")
    private String manualContactUpdatedBy;

    @TableField(value = "raw_payload", typeHandler = com.colonel.saas.common.typehandler.JsonbTypeHandler.class)
    private java.util.Map<String, Object> rawPayload;

    @TableField("source_updated_at")
    private LocalDateTime sourceUpdatedAt;
}
