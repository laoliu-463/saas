package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * 达人投诉事实，记录投诉关联的合作单、达人、商品与报告人。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_complaint")
public class TalentComplaint extends VersionedEntity {

    @TableField("sample_request_id")
    private UUID sampleRequestId;

    @TableField("talent_id")
    private UUID talentId;

    @TableField("product_id")
    private UUID productId;

    @TableField("reporter_user_id")
    private UUID reporterUserId;

    @TableField("reason_code")
    private String reasonCode;

    private String content;

    private String status = "SUBMITTED";
}
