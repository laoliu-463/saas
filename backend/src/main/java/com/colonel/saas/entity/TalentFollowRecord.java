package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 达人跟进记录实体。
 * <p>
 * 对应数据库表：{@code talent_follow_record}，记录业务人员对达人的每次跟进沟通情况。
 * 跟进记录关联商品和活动上下文，跟踪跟进建议状态、沟通内容、下次跟进计划和操作人，
 * 用于达人关系维护和跟进管理。
 * 继承 {@link com.colonel.saas.common.base.BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see Talent 达人主实体
 * @see TalentClaim 达人认领记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_follow_record")
public class TalentFollowRecord extends BaseEntity {

    /**
     * 商品 ID
     * <p>对应数据库列：{@code product_id}，跟进所关联的商品标识，
     * 可为空（非商品相关的通用跟进场景）</p>
     */
    @TableField("product_id")
    private String productId;

    /**
     * 活动 ID
     * <p>对应数据库列：{@code activity_id}，跟进所关联的团长活动标识，
     * 可为空（非活动相关的通用跟进场景）</p>
     */
    @TableField("activity_id")
    private String activityId;

    /**
     * 达人 ID
     * <p>对应数据库列：{@code talent_id}，关联达人主表，标识被跟进的达人</p>
     */
    @TableField("talent_id")
    private UUID talentId;

    /**
     * 达人名称
     * <p>对应数据库列：{@code talent_name}，冗余存储达人昵称，便于列表展示，
     * 避免频繁关联查询达人表</p>
     */
    @TableField("talent_name")
    private String talentName;

    /**
     * 跟进状态
     * <p>对应数据库列：{@code follow_status}，标识当前跟进的阶段或结果，
     * 如 "INITIAL"（初次联系）、"FOLLOWING"（跟进中）、"ACCEPTED"（已接受）、
     * "REJECTED"（已拒绝）、"COMPLETED"（已完成）等</p>
     */
    @TableField("follow_status")
    private String followStatus;

    /**
     * 跟进内容
     * <p>记录本次跟进的沟通详情、反馈信息、备注等文本内容</p>
     */
    private String content;

    /**
     * 下次跟进时间
     * <p>对应数据库列：{@code next_follow_time}，计划的下次跟进时间点，
     * 用于提醒业务人员及时跟进</p>
     */
    @TableField("next_follow_time")
    private LocalDateTime nextFollowTime;

    /**
     * 操作人 ID
     * <p>对应数据库列：{@code operator_id}，执行本次跟进操作的业务人员标识</p>
     */
    @TableField("operator_id")
    private UUID operatorId;

    /**
     * 操作人名称
     * <p>对应数据库列：{@code operator_name}，冗余存储操作人姓名，便于列表展示</p>
     */
    @TableField("operator_name")
    private String operatorName;
}
