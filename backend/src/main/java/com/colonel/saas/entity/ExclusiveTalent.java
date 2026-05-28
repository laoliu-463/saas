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
 * 独家达人实体。
 * <p>
 * 对应数据库表：{@code exclusive_talent}，记录团长与达人签订的独家合作协议信息。
 * 独家达人协议包含独家类型、服务费、渠道费、月寄样数等商务条款，
 * 用于业绩归属中的达人维度排他判断和提成计算。
 * 继承 {@link BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see Talent 达人主实体
 * @see TalentClaim 达人认领记录
 * @see PerformanceRecord 业绩记录，使用独家达人信息进行归属计算
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exclusive_talent")
public class ExclusiveTalent extends BaseEntity {

    /**
     * 达人 ID
     * <p>对应数据库列：{@code talent_id}，关联达人主表</p>
     */
    @TableField("talent_id")
    private UUID talentId;

    /**
     * 达人 UID（外部平台标识）
     * <p>对应数据库列：{@code talent_uid}，抖音平台的达人唯一标识</p>
     */
    @TableField("talent_uid")
    private String talentUid;

    /**
     * 负责人用户 ID
     * <p>对应数据库列：{@code user_id}，该独家达人在系统中归属的业务负责人</p>
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
     * 独家类型
     * <p>对应数据库列：{@code exclusive_type}，标识独家合作的类型分类，
     * 具体取值参考业务字典</p>
     */
    @TableField("exclusive_type")
    private Integer exclusiveType;

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
     * 渠道总费用（单位：分）
     * <p>对应数据库列：{@code channel_total_fee}，合作期间的渠道总费用额度</p>
     */
    @TableField("channel_total_fee")
    private Long channelTotalFee;

    /**
     * 服务费比例
     * <p>对应数据库列：{@code service_fee_ratio}，百分比形式的服务费率</p>
     */
    @TableField("service_fee_ratio")
    private BigDecimal serviceFeeRatio;

    /**
     * 每月寄样数量
     * <p>对应数据库列：{@code monthly_samples}，协议约定的每月寄送样品数量</p>
     */
    @TableField("monthly_samples")
    private Integer monthlySamples;

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

    /**
     * 触发类型
     * <p>对应数据库列：{@code trigger_type}，标识该独家协议的触发方式，
     * 如手动创建或系统自动匹配等</p>
     */
    @TableField("trigger_type")
    private Integer triggerType;
}
