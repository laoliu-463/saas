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
@TableName("talent_follow_record")
public class TalentFollowRecord extends BaseEntity {

    @TableField("product_id")
    private String productId;

    @TableField("activity_id")
    private String activityId;

    @TableField("talent_id")
    private UUID talentId;

    @TableField("talent_name")
    private String talentName;

    @TableField("follow_status")
    private String followStatus;

    private String content;

    @TableField("next_follow_time")
    private LocalDateTime nextFollowTime;

    @TableField("operator_id")
    private UUID operatorId;

    @TableField("operator_name")
    private String operatorName;
}
