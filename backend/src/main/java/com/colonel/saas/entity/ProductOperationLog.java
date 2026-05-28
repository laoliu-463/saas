package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * 商品操作日志实体。
 * <p>
 * 对应数据库表：{@code product_operation_log}，记录商品在活动上下文中发生的所有
 * 业务操作（如转链、上下架、置顶、隐藏等），用于操作审计和问题追溯。
 * 继承 {@link BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see Product 商品主实体
 * @see ProductOperationState 商品操作状态
 * @see OperationLog 通用操作日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_operation_log")
public class ProductOperationLog extends BaseEntity {

    /**
     * 活动 ID
     * <p>对应数据库列：{@code activity_id}，商品操作所关联的团长活动标识</p>
     */
    @TableField("activity_id")
    private String activityId;

    /**
     * 商品 ID
     * <p>对应数据库列：{@code product_id}，被操作的商品标识</p>
     */
    @TableField("product_id")
    private String productId;

    /**
     * 操作类型
     * <p>对应数据库列：{@code operation_type}，如 "PROMOTE"（转链）、"ON_SHELF"（上架）、
     * "OFF_SHELF"（下架）、"PIN"（置顶）、"HIDE"（隐藏）等</p>
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * 操作前状态
     * <p>对应数据库列：{@code before_status}，操作执行前商品的状态快照</p>
     */
    @TableField("before_status")
    private String beforeStatus;

    /**
     * 操作后状态
     * <p>对应数据库列：{@code after_status}，操作执行后商品的状态快照</p>
     */
    @TableField("after_status")
    private String afterStatus;

    /**
     * 操作是否成功
     * <p>true=成功, false=失败。失败时 error_message 记录具体错误信息</p>
     */
    private Boolean success;

    /**
     * 错误信息
     * <p>对应数据库列：{@code error_message}，操作失败时记录的具体错误描述</p>
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 操作请求载荷
     * <p>对应数据库列：{@code operation_payload}，JSON 格式，记录操作请求的参数快照，
     * 便于问题排查和操作回放</p>
     */
    @TableField("operation_payload")
    private String operationPayload;

    /**
     * 操作备注
     * <p>对应数据库列：{@code operation_remark}，操作人填写的备注说明</p>
     */
    @TableField("operation_remark")
    private String operationRemark;

    /**
     * 操作人 ID
     * <p>对应数据库列：{@code operator_id}，执行该操作的系统用户标识</p>
     */
    @TableField("operator_id")
    private UUID operatorId;

    /**
     * 操作人所属部门 ID
     * <p>对应数据库列：{@code operator_dept_id}，用于部门级别的审计统计</p>
     */
    @TableField("operator_dept_id")
    private UUID operatorDeptId;
}
