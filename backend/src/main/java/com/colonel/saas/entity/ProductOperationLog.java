package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_operation_log")
public class ProductOperationLog extends BaseEntity {

    @TableField("activity_id")
    private String activityId;

    @TableField("product_id")
    private String productId;

    @TableField("operation_type")
    private String operationType;

    @TableField("operation_payload")
    private String operationPayload;

    @TableField("operation_remark")
    private String operationRemark;

    @TableField("operator_id")
    private UUID operatorId;

    @TableField("operator_dept_id")
    private UUID operatorDeptId;
}

