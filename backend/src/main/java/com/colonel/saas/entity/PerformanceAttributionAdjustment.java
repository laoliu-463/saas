package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 业绩归属人工调整事实。
 *
 * <p>只保存经审批的调整输入，不直接改写订单默认归属或历史业绩；业绩计算时按订单发生时间读取。
 * 同一订单允许保留多条历史调整，当前仅采用状态为 APPROVED 且处于有效期的最新记录。</p>
 */
@Data
@TableName("performance_attribution_adjustment")
public class PerformanceAttributionAdjustment {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("order_id")
    private String orderId;

    @TableField("channel_user_id")
    private UUID channelUserId;

    @TableField("recruiter_user_id")
    private UUID recruiterUserId;

    @TableField("channel_dept_id")
    private UUID channelDeptId;

    @TableField("recruiter_dept_id")
    private UUID recruiterDeptId;

    @TableField("effective_from")
    private LocalDateTime effectiveFrom;

    @TableField("effective_until")
    private LocalDateTime effectiveUntil;

    /** PENDING / APPROVED / REVOKED。 */
    private String status;

    private String reason;

    @TableField("approved_by")
    private UUID approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
