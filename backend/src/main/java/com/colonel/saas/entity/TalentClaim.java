package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_claim")
public class TalentClaim extends VersionedEntity {

    @TableField("talent_id")
    private UUID talentId;

    @TableField("user_id")
    private UUID userId;

    @TableField("talent_uid")
    private String talentUid;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("claim_type")
    private Integer claimType;

    @TableField("apply_time")
    private LocalDateTime claimedAt;

    @TableField("expire_time")
    private LocalDateTime protectedUntil;

    @TableField("recipient_name")
    private String recipientName;

    @TableField("recipient_phone")
    private String recipientPhone;

    @TableField("recipient_address")
    private String recipientAddress;

    private Integer status;
}
