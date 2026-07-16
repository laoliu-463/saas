package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 达人投诉接收人提醒及其已读状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_complaint_reminder")
public class TalentComplaintReminder extends VersionedEntity {

    @TableField("complaint_id")
    private UUID complaintId;

    @TableField("recipient_user_id")
    private UUID recipientUserId;

    @TableField("read_at")
    private LocalDateTime readAt;
}
