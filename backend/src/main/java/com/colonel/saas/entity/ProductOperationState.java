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
@TableName("product_operation_state")
public class ProductOperationState extends BaseEntity {

    @TableField("activity_id")
    private String activityId;

    @TableField("product_id")
    private String productId;

    @TableField("bound_activity_id")
    private String boundActivityId;

    @TableField("biz_status")
    private String bizStatus;

    @TableField("assignee_id")
    private UUID assigneeId;

    @TableField("audit_status")
    private Integer auditStatus;

    @TableField("audit_remark")
    private String auditRemark;

    @TableField("promote_link")
    private String promoteLink;

    @TableField("short_link")
    private String shortLink;

    @TableField("promotion_scene")
    private Integer promotionScene;

    @TableField("external_unique_id")
    private String externalUniqueId;

    @TableField("last_operation_at")
    private LocalDateTime lastOperationAt;
}
