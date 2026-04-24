package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_auth")
public class TalentAuth extends BaseEntity {

    @TableField("talent_id")
    private UUID talentId;

    @TableField("open_id")
    private String openId;

    @TableField("union_id")
    private String unionId;

    @TableField("access_token")
    private String accessToken;

    @TableField("refresh_token")
    private String refreshToken;

    @TableField("scope")
    private String scope;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField("auth_time")
    private LocalDateTime authTime;

    @TableField("status")
    private String status;
}

