package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 独家商家实体。
 * <p>
 * 对应数据库表：{@code exclusive_merchant}，记录团长与商家签订的独家合作协议信息。
 * 独家商家协议涉及服务费、合作期限、有效月份等核心商务条款，用于业绩归属和提成计算。
 * 继承 {@link BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see Merchant 商家主实体
 * @see PerformanceRecord 业绩记录，使用独家商家信息进行归属计算
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exclusive_merchant")
public class ExclusiveMerchant extends BaseEntity {

    /**
     * 商家 ID
     * <p>对应数据库列：{@code merchant_id}，关联商家主表的商家标识</p>
     */
    @TableField("merchant_id")
    private String merchantId;

    /**
     * 商家名称
     * <p>对应数据库列：{@code merchant_name}，冗余存储便于展示</p>
     */
    @TableField("merchant_name")
    private String merchantName;

    /**
     * 店铺 ID
     * <p>对应数据库列：{@code shop_id}，关联抖店店铺标识</p>
     */
    @TableField("shop_id")
    private Long shopId;

    /**
     * 负责人用户 ID
     * <p>对应数据库列：{@code user_id}，该独家商家在系统中归属的业务负责人</p>
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 负责人所属部门 ID
     * <p>对应数据库列：{@code dept_id}，用于数据范围过滤（self/group/all）</p>
     */
    @TableField("dept_id")
    private UUID deptId;

    /**
     * 生效月份
     * <p>对应数据库列：{@code effective_month}，格式如 "202605"，标识协议生效的月份</p>
     */
    @TableField("effective_month")
    private String effectiveMonth;

    /**
     * 服务费金额（单位：分）
     * <p>对应数据库列：{@code service_fee}，固定金额形式的服务费</p>
     */
    @TableField("service_fee")
    private Long serviceFee;

    /**
     * 业务总费用（单位：分）
     * <p>对应数据库列：{@code business_total_fee}，合作期间的业务总额度</p>
     */
    @TableField("business_total_fee")
    private Long businessTotalFee;

    /**
     * 服务费比例
     * <p>对应数据库列：{@code service_fee_ratio}，百分比形式的服务费率，
     * 用于按比例计算服务费（与固定金额 serviceFee 二选一使用）</p>
     */
    @TableField("service_fee_ratio")
    private BigDecimal serviceFeeRatio;

    /**
     * 合作开始日期
     * <p>对应数据库列：{@code start_date}，独家协议的起始日期</p>
     */
    @TableField("start_date")
    private LocalDate startDate;

    /**
     * 合作结束日期
     * <p>对应数据库列：{@code end_date}，独家协议的截止日期</p>
     */
    @TableField("end_date")
    private LocalDate endDate;

    /**
     * 状态
     * <p>1=生效中, 0=已失效。用于判断当前独家协议是否仍在有效期内</p>
     */
    private Integer status;

    /**
     * 备注
     * <p>协议相关的补充说明信息</p>
     */
    private String remark;
}
