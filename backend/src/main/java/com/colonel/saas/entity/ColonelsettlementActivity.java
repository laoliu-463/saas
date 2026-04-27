package com.colonel.saas.entity;

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
@TableName("colonelsettlement_activity")
public class ColonelsettlementActivity extends BaseEntity {

    private String name;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /**
     * 1=进行中, 0=已结束。
     */
    private Integer status;
}

