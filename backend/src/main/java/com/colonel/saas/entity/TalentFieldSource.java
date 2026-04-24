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
@TableName("talent_field_source")
public class TalentFieldSource extends BaseEntity {

    @TableField("talent_id")
    private UUID talentId;

    @TableField("field_name")
    private String fieldName;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_value")
    private String sourceValue;

    @TableField("source_ref_type")
    private String sourceRefType;

    @TableField("source_ref_id")
    private String sourceRefId;

    @TableField("verified_by")
    private UUID verifiedBy;

    @TableField("verified_time")
    private LocalDateTime verifiedTime;
}

