package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 团长活动实体。
 * <p>
 * 对应数据库表：{@code colonel_activity}，记录团长在抖店平台发起的活动信息。
 * 活动包含佣金率、服务费率、保护期等核心商务参数，关联具体的店铺和团长百应 ID。
 * 活动数据来源于抖店平台同步，是业绩归属和提成计算的重要上下文。
 * 继承 {@link com.colonel.saas.common.base.BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see ColonelsettlementOrder 结算订单，关联活动上下文
 * @see PerformanceRecord 业绩记录，基于活动进行归属计算
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "colonel_activity", autoResultMap = true)
public class ColonelsettlementActivity extends BaseEntity {

    /**
     * 活动 ID
     * <p>对应数据库列：{@code activity_id}，抖店平台的活动唯一标识</p>
     */
    @TableField("activity_id")
    private String activityId;

    /**
     * 活动名称
     * <p>对应数据库列：{@code activity_name}，活动的显示名称</p>
     */
    @TableField("activity_name")
    private String name;

    /**
     * 活动类型
     * <p>对应数据库列：{@code activity_type}，标识活动的业务分类，
     * 如普通活动、专属活动等，具体取值参考业务字典</p>
     */
    @TableField("activity_type")
    private String activityType;

    /**
     * 店铺 ID
     * <p>对应数据库列：{@code shop_id}，活动关联的抖店店铺标识</p>
     */
    @TableField("shop_id")
    private Long shopId;

    /**
     * 店铺名称
     * <p>对应数据库列：{@code shop_name}，冗余存储店铺名称，便于展示</p>
     */
    @TableField("shop_name")
    private String shopName;

    /**
     * 团长百应 ID
     * <p>对应数据库列：{@code colonel_buyin_id}，抖店平台的团长百应唯一标识，
     * 用于与平台侧进行数据关联</p>
     */
    @TableField("colonel_buyin_id")
    private Long colonelBuyinId;

    /**
     * 佣金率
     * <p>对应数据库列：{@code commission_rate}，活动约定的佣金比例，
     * 用于计算达人佣金分成</p>
     */
    @TableField("commission_rate")
    private BigDecimal commissionRate;

    /**
     * 服务费率
     * <p>对应数据库列：{@code service_rate}，活动约定的服务费比例，
     * 用于计算平台服务费收入</p>
     */
    @TableField("service_rate")
    private BigDecimal serviceRate;

    /**
     * 活动开始时间
     * <p>对应数据库列：{@code start_time}，活动生效的起始时间</p>
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 活动结束时间
     * <p>对应数据库列：{@code end_time}，活动失效的截止时间</p>
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 最近同步时间
     * <p>对应数据库列：{@code last_sync_at}，最后一次从抖店平台同步活动数据的时间，
     * 用于增量同步和数据新鲜度判断</p>
     */
    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    /**
     * 保护期月数
     * <p>对应数据库列：{@code months_of_protection}，活动的业绩保护期时长（月），
     * 保护期内产生的订单业绩归属于原团长</p>
     */
    @TableField("months_of_protection")
    private Integer monthsOfProtection;

    /**
     * 扩展数据
     * <p>对应数据库列：{@code extra_data}，JSON 格式，存储活动的附加属性和扩展信息，
     * 由 JacksonTypeHandler 自动序列化/反序列化</p>
     */
    @TableField(value = "extra_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;

    /**
     * 活动状态码（来自抖店平台）。
     * <p>1=未上线, 2=报名未开始, 3=报名中, 4=推广未开始, 5=推广中, 7=已结束</p>
     */
    @TableField("status")
    private Integer status;

    /**
     * 抖店活动状态码（列表/详情同步落库，用于推广中判定）。
     */
    @TableField("activity_status_code")
    private Integer activityStatusCode;

    /**
     * 抖店活动状态文案（如「推广中」）。
     */
    @TableField("activity_status_text")
    private String activityStatusText;

    /** 活动级招商组长用户 ID */
    @TableField("recruiter_user_id")
    private UUID recruiterUserId;

    /** 活动级招商组长部门 ID */
    @TableField("recruiter_dept_id")
    private UUID recruiterDeptId;

    /** 活动分配给招商组长的时间 */
    @TableField("assigned_at")
    private LocalDateTime assignedAt;

    /** 执行活动分配的管理员用户 ID */
    @TableField("assigned_by")
    private UUID assignedBy;
}

