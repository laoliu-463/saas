package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName(value = "product_display_audit_log", autoResultMap = true)
public class ProductDisplayAuditLog {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    @TableField("product_id")
    private String productId;

    @TableField("old_relation_id")
    private UUID oldRelationId;

    @TableField("new_relation_id")
    private UUID newRelationId;

    @TableField(value = "candidate_relation_ids", typeHandler = JsonbTypeHandler.class)
    private String candidateRelationIds;

    @TableField("action_type")
    private String actionType;

    @TableField("selected_reason")
    private String selectedReason;

    @TableField("hidden_reason")
    private String hiddenReason;

    @TableField("rule_version")
    private Integer ruleVersion;

    @TableField("operator_type")
    private String operatorType;

    @TableField("operator_id")
    private String operatorId;

    @TableField(value = "detail_json", typeHandler = JsonbTypeHandler.class)
    private String detailJson;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
