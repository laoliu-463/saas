package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 业绩记录实体。
 * <p>
 * 对应数据库表：{@code performance_records}，记录每笔订单的业绩归属和提成计算结果。
 * 业绩记录是业绩域的核心实体，负责最终归属判定（渠道/招募双轨）、佣金计算、
 * 冲正标记和双轨金额计算。每笔结算订单对应一条业绩记录，包含预估（estimate）
 * 和实际（effective）两套金额字段，分别在订单预结算和正式结算时填充。
 * 不继承 BaseEntity，采用手动输入的 UUID 主键和手动审计字段。
 * </p>
 *
 * @see ColonelsettlementOrder 结算订单，业绩记录的数据来源
 * @see ExclusiveTalent 独家达人，用于归属计算中的排他判断
 * @see ExclusiveMerchant 独家商家，用于归属计算中的排他判断
 * @see CommissionRule 佣金规则，提供佣金率配置
 */
@Data
@TableName("performance_records")
public class PerformanceRecord {

    /**
     * 主键 ID
     * <p>手动输入的 UUID 主键</p>
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 订单 ID
     * <p>对应数据库列：{@code order_id}，关联抖店平台的订单标识</p>
     */
    @TableField("order_id")
    private String orderId;

    /**
     * 订单行 ID
     * <p>对应数据库列：{@code order_row_id}，关联结算订单表的主键，
     * 用于精确定位到 colonelsettlement_order 中的具体记录</p>
     */
    @TableField("order_row_id")
    private UUID orderRowId;

    /**
     * 默认渠道用户 ID
     * <p>对应数据库列：{@code default_channel_user_id}，基于推广来源默认归属的渠道业务人员</p>
     */
    @TableField("default_channel_user_id")
    private UUID defaultChannelUserId;

    /**
     * 默认招募用户 ID
     * <p>对应数据库列：{@code default_recruiter_user_id}，基于达人认领关系默认归属的招募人员</p>
     */
    @TableField("default_recruiter_user_id")
    private UUID defaultRecruiterUserId;

    /**
     * 最终渠道用户 ID
     * <p>对应数据库列：{@code final_channel_user_id}，经过归属规则调整后的最终渠道归属人员，
     * 可能与默认值不同（如独家协议覆盖）</p>
     */
    @TableField("final_channel_user_id")
    private UUID finalChannelUserId;

    /**
     * 最终招募用户 ID
     * <p>对应数据库列：{@code final_recruiter_user_id}，经过归属规则调整后的最终招募归属人员</p>
     */
    @TableField("final_recruiter_user_id")
    private UUID finalRecruiterUserId;

    /**
     * 渠道归属方式
     * <p>对应数据库列：{@code channel_attribution}，标识渠道归属的规则依据，
     * 如 "PICK_SOURCE"（推广来源）、"EXCLUSIVE"（独家协议）等</p>
     */
    @TableField("channel_attribution")
    private String channelAttribution;

    /**
     * 招募归属方式
     * <p>对应数据库列：{@code recruiter_attribution}，标识招募归属的规则依据，
     * 如 "CLAIM"（达人认领）、"EXCLUSIVE"（独家协议）等</p>
     */
    @TableField("recruiter_attribution")
    private String recruiterAttribution;

    /**
     * 达人 ID
     * <p>对应数据库列：{@code talent_id}，订单关联的达人标识</p>
     */
    @TableField("talent_id")
    private UUID talentId;

    /**
     * 合作伙伴 ID
     * <p>对应数据库列：{@code partner_id}，关联团长合作伙伴表的百应 ID</p>
     */
    @TableField("partner_id")
    private Long partnerId;

    /**
     * 商品 ID
     * <p>对应数据库列：{@code product_id}，订单关联的商品标识</p>
     */
    @TableField("product_id")
    private String productId;

    /**
     * 活动 ID
     * <p>对应数据库列：{@code activity_id}，订单关联的团长活动标识</p>
     */
    @TableField("activity_id")
    private String activityId;

    /**
     * 实付金额（单位：分）
     * <p>对应数据库列：{@code pay_amount}，用户实际支付的金额</p>
     */
    @TableField("pay_amount")
    private Long payAmount;

    /**
     * 结算金额（单位：分）
     * <p>对应数据库列：{@code settle_amount}，平台最终结算金额</p>
     */
    @TableField("settle_amount")
    private Long settleAmount;

    /**
     * 预估服务费（单位：分）
     * <p>对应数据库列：{@code estimate_service_fee}，预估阶段计算的服务费</p>
     */
    @TableField("estimate_service_fee")
    private Long estimateServiceFee;

    /**
     * 实际服务费（单位：分）
     * <p>对应数据库列：{@code effective_service_fee}，结算确认后的实际服务费</p>
     */
    @TableField("effective_service_fee")
    private Long effectiveServiceFee;

    /**
     * 预估技术服务费（单位：分）
     * <p>对应数据库列：{@code estimate_tech_service_fee}，预估阶段计算的技术服务费</p>
     */
    @TableField("estimate_tech_service_fee")
    private Long estimateTechServiceFee;

    /**
     * 实际技术服务费（单位：分）
     * <p>对应数据库列：{@code effective_tech_service_fee}，结算确认后的实际技术服务费</p>
     */
    @TableField("effective_tech_service_fee")
    private Long effectiveTechServiceFee;

    /**
     * 预估服务费支出（单位：分）
     * <p>对应数据库列：{@code estimate_service_fee_expense}，独立外部成本字段</p>
     */
    @TableField("estimate_service_fee_expense")
    private Long estimateServiceFeeExpense;

    /**
     * 实际服务费支出（单位：分）
     * <p>对应数据库列：{@code effective_service_fee_expense}，独立外部成本字段</p>
     */
    @TableField("effective_service_fee_expense")
    private Long effectiveServiceFeeExpense;

    /**
     * 预估服务利润（单位：分）
     * <p>对应数据库列：{@code estimate_service_profit}，预估阶段计算的服务利润</p>
     */
    @TableField("estimate_service_profit")
    private Long estimateServiceProfit;

    /**
     * 实际服务利润（单位：分）
     * <p>对应数据库列：{@code effective_service_profit}，结算确认后的实际服务利润</p>
     */
    @TableField("effective_service_profit")
    private Long effectiveServiceProfit;

    /**
     * 预估招募佣金（单位：分）
     * <p>对应数据库列：{@code estimate_recruiter_commission}，预估阶段计算的招募人员佣金</p>
     */
    @TableField("estimate_recruiter_commission")
    private Long estimateRecruiterCommission;

    /**
     * 实际招募佣金（单位：分）
     * <p>对应数据库列：{@code effective_recruiter_commission}，结算确认后的实际招募佣金</p>
     */
    @TableField("effective_recruiter_commission")
    private Long effectiveRecruiterCommission;

    /**
     * 预估渠道佣金（单位：分）
     * <p>对应数据库列：{@code estimate_channel_commission}，预估阶段计算的渠道人员佣金</p>
     */
    @TableField("estimate_channel_commission")
    private Long estimateChannelCommission;

    /**
     * 实际渠道佣金（单位：分）
     * <p>对应数据库列：{@code effective_channel_commission}，结算确认后的实际渠道佣金</p>
     */
    @TableField("effective_channel_commission")
    private Long effectiveChannelCommission;

    /**
     * 预估毛利润（单位：分）
     * <p>对应数据库列：{@code estimate_gross_profit}，预估阶段计算的综合毛利润</p>
     */
    @TableField("estimate_gross_profit")
    private Long estimateGrossProfit;

    /**
     * 实际毛利润（单位：分）
     * <p>对应数据库列：{@code effective_gross_profit}，结算确认后的实际毛利润</p>
     */
    @TableField("effective_gross_profit")
    private Long effectiveGrossProfit;

    /**
     * 招募佣金率
     * <p>对应数据库列：{@code recruiter_commission_rate}，招募人员的佣金比例</p>
     */
    @TableField("recruiter_commission_rate")
    private BigDecimal recruiterCommissionRate;

    /**
     * 渠道佣金率
     * <p>对应数据库列：{@code channel_commission_rate}，渠道人员的佣金比例</p>
     */
    @TableField("channel_commission_rate")
    private BigDecimal channelCommissionRate;

    /**
     * 订单状态
     * <p>对应数据库列：{@code order_status}，关联订单的当前状态</p>
     */
    @TableField("order_status")
    private Integer orderStatus;

    /**
     * 结算时间
     * <p>对应数据库列：{@code settle_time}，订单完成结算的时间</p>
     */
    @TableField("settle_time")
    private LocalDateTime settleTime;

    /**
     * 订单创建时间
     * <p>对应数据库列：{@code order_create_time}，原始订单的创建时间</p>
     */
    @TableField("order_create_time")
    private LocalDateTime orderCreateTime;

    /**
     * 是否有效
     * <p>对应数据库列：{@code is_valid}，标记该业绩记录是否有效。
     * 退款等场景下会被标记为无效</p>
     */
    @TableField("is_valid")
    private Boolean valid;

    /**
     * 是否已冲正
     * <p>对应数据库列：{@code is_reversed}，标记该业绩记录是否已被冲正。
     * 冲正操作会生成新的反向记录来抵消原记录</p>
     */
    @TableField("is_reversed")
    private Boolean reversed;

    /**
     * 计算版本号
     * <p>对应数据库列：{@code calculation_version}，业绩计算算法的版本标识，
     * 用于在算法升级后标记需要重新计算的记录</p>
     */
    @TableField("calculation_version")
    private Integer calculationVersion;

    /**
     * 计算时间
     * <p>对应数据库列：{@code calculated_at}，最后一次执行业绩计算的时间</p>
     */
    @TableField("calculated_at")
    private LocalDateTime calculatedAt;

    /**
     * 创建时间
     * <p>对应数据库列：{@code created_at}，记录入库时间</p>
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * <p>对应数据库列：{@code updated_at}，记录最后变更时间</p>
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
