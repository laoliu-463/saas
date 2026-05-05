package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 团长活动（Test 阶段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("colonel_activity")
public class ColonelsettlementActivity extends BaseEntity {

    @TableField("activity_id")
    private String activityId;

    @TableField("activity_name")
    private String name;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 1=进行中, 0=已结束。
     */
    @TableField(exist = false)
    private Integer status;
}

