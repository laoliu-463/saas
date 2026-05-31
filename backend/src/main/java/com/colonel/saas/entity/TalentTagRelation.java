package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_tag_relation")
public class TalentTagRelation extends BaseEntity {

    @TableField("talent_id")
    private UUID talentId;

    @TableField("tag_id")
    private UUID tagId;

    @TableField("create_user_id")
    private UUID createUserId;
}
