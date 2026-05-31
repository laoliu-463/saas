package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_tag")
public class TalentTag extends BaseEntity {

    @TableField("tag_name")
    private String tagName;

    @TableField("tag_type")
    private String tagType;
}
