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
@TableName("pick_source_mapping")
public class PickSourceMapping extends BaseEntity {

    @TableField("user_id")
    private UUID userId;

    @TableField("short_id")
    private String shortId;

    @TableField("uuid_seed")
    private UUID uuidSeed;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("pick_source")
    private String pickSource;

    @TableField("product_id")
    private String productId;

    @TableField("activity_id")
    private String activityId;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("converted_url")
    private String convertedUrl;

    @TableField("pick_extra")
    private String pickExtra;

    @TableField("valid_from")
    private LocalDateTime validFrom;

    @TableField("valid_until")
    private LocalDateTime validUntil;

    private Integer status;
}
