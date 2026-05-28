package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品展示审计日志实体。
 * <p>
 * 对应数据库表：{@code product_display_audit_log}，记录商品关联关系变更的审计日志。
 * 不继承 BaseEntity（独立管理 ID 和时间字段），ID 由调用方指定（{@link IdType#INPUT}）。
 * 当商品的活动关联关系发生切换（如从一个活动转移到另一个活动）时，系统自动记录此日志。
 * </p>
 *
 * @see ProductOperationState 商品运营状态
 * @see Product 商品实体
 */
@Data
@TableName(value = "product_display_audit_log", autoResultMap = true)
public class ProductDisplayAuditLog {

    /**
     * 主键 ID
     * <p>由调用方指定的 UUID 主键（{@link IdType#INPUT}），通常使用 UUID.randomUUID()</p>
     */
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    /**
     * 商品 ID
     * <p>对应数据库列：{@code product_id}，关联的商品标识</p>
     */
    @TableField("product_id")
    private String productId;

    /**
     * 原关联 ID
     * <p>对应数据库列：{@code old_relation_id}，变更前的商品活动关联 ID</p>
     */
    @TableField("old_relation_id")
    private UUID oldRelationId;

    /**
     * 新关联 ID
     * <p>对应数据库列：{@code new_relation_id}，变更后的商品活动关联 ID</p>
     */
    @TableField("new_relation_id")
    private UUID newRelationId;

    /**
     * 候选关联 ID 列表
     * <p>JSON 数组格式，对应数据库列：{@code candidate_relation_ids}，
     * 变更时可选的所有候选关联 ID 列表</p>
     */
    @TableField(value = "candidate_relation_ids", typeHandler = JsonbTypeHandler.class)
    private String candidateRelationIds;

    /**
     * 操作类型
     * <p>对应数据库列：{@code action_type}，标识关联变更的操作类型，
     * 如 "AUTO_SWITCH"（自动切换）、"MANUAL_SELECT"（手动选择）等</p>
     */
    @TableField("action_type")
    private String actionType;

    /**
     * 选择原因
     * <p>对应数据库列：{@code selected_reason}，系统选择新关联的原因说明</p>
     */
    @TableField("selected_reason")
    private String selectedReason;

    /**
     * 隐藏原因
     * <p>对应数据库列：{@code hidden_reason}，如果商品被隐藏，记录隐藏的原因</p>
     */
    @TableField("hidden_reason")
    private String hiddenReason;

    /**
     * 规则版本号
     * <p>对应数据库列：{@code rule_version}，执行此变更时使用的规则版本</p>
     */
    @TableField("rule_version")
    private Integer ruleVersion;

    /**
     * 操作者类型
     * <p>对应数据库列：{@code operator_type}，标识操作的执行者类型，
     * 如 "SYSTEM"（系统自动）、"USER"（人工操作）</p>
     */
    @TableField("operator_type")
    private String operatorType;

    /**
     * 操作者 ID
     * <p>对应数据库列：{@code operator_id}，执行操作的用户 ID 或系统标识</p>
     */
    @TableField("operator_id")
    private String operatorId;

    /**
     * 详细信息 JSON
     * <p>JSON 对象格式，对应数据库列：{@code detail_json}，
     * 存储变更的详细上下文信息，用于审计排查</p>
     */
    @TableField(value = "detail_json", typeHandler = JsonbTypeHandler.class)
    private String detailJson;

    /**
     * 记录创建时间
     * <p>对应数据库列：{@code created_at}，审计日志的入库时间</p>
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
