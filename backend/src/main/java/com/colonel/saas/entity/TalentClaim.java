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
@TableName("talent_claim")
public class TalentClaim extends BaseEntity {

    @TableField("talent_id")
    private UUID talentId;

    @TableField("user_id")
    private UUID userId;

    @TableField("talent_uid")
    private String talentUid;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("apply_time")
    private LocalDateTime claimedAt;

    @TableField("expire_time")
    private LocalDateTime protectedUntil;

    private Integer status;
}
