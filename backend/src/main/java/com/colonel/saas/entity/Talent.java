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
@TableName("talent")
public class Talent extends BaseEntity {

    @TableField("douyin_uid")
    private String douyinUid;

    private String nickname;

    @TableField("fans_count")
    private Long fans;

    @TableField("fans_level")
    private String level;

    private Integer status;

    @TableField(exist = false)
    private Long monthlySales;

    @TableField(exist = false)
    private UUID ownerId;

    @TableField(exist = false)
    private LocalDateTime claimedAt;
}
