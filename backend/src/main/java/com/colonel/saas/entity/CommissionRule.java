package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 佣金规则实体。
 * <p>
 * 对应数据库表：{@code commissions}，定义按维度（达人/商品/类目等）的佣金比例配置。
 * 业绩域根据佣金规则计算提成金额，规则支持有效期和多维度配置。
 * 继承 {@link BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see PerformanceRecord 业绩记录，使用佣金规则计算提成
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("commissions")
public class CommissionRule extends BaseEntity {

    /**
     * 维度类型
     * <p>对应数据库列：{@code dimension_type}，标识佣金规则适用的维度，
     * 如 "TALENT"（达人维度）、"PRODUCT"（商品维度）、"CATEGORY"（类目维度）等</p>
     */
    @TableField("dimension_type")
    private String dimensionType;

    /**
     * 维度 ID
     * <p>对应数据库列：{@code dimension_id}，对应维度类型的具体对象 ID，
     * 如达人 ID、商品 ID 或类目编码</p>
     */
    @TableField("dimension_id")
    private String dimensionId;

    /**
     * 佣金类型
     * <p>对应数据库列：{@code commission_type}，佣金的业务分类，
     * 如 "SERVICE_FEE"（服务费）、"CPS"（CPS 佣金）等</p>
     */
    @TableField("commission_type")
    private String commissionType;

    /**
     * 佣金比例
     * <p>对应数据库列：{@code ratio}，佣金计算比例，使用 BigDecimal 存储以保证精度</p>
     */
    private BigDecimal ratio;

    /**
     * 生效开始时间
     * <p>对应数据库列：{@code effective_start}，佣金规则的生效起始时间</p>
     */
    @TableField("effective_start")
    private LocalDateTime effectiveStart;

    /**
     * 生效结束时间
     * <p>对应数据库列：{@code effective_end}，佣金规则的生效截止时间，null 表示长期有效</p>
     */
    @TableField("effective_end")
    private LocalDateTime effectiveEnd;

    /**
     * 状态
     * <p>1=启用, 0=禁用。控制佣金规则是否参与提成计算</p>
     */
    private Integer status;
}
