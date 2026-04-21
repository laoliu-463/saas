package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_request")
public class SampleRequest extends BaseEntity {

    @TableField("request_no")
    private String requestNo;

    @TableField("talent_id")
    private UUID talentId;

    @TableField("talent_uid")
    private String talentUid;

    @TableField("talent_nickname")
    private String talentNickname;

    @TableField("talent_fans_count")
    private Long talentFansCount;

    @TableField("talent_credit_score")
    private BigDecimal talentCreditScore;

    @TableField("talent_main_category")
    private String talentMainCategory;

    @TableField("product_id")
    private UUID productId;

    @TableField("user_id")
    private UUID userId;

    @TableField("channel_user_id")
    private UUID channelUserId;

    @TableField("expected_sample_num")
    private Integer expectedSampleNum;

    @TableField("actual_sample_num")
    private Integer actualSampleNum;

    @TableField("tracking_no")
    private String trackingNo;

    private Integer status;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("audit_time")
    private LocalDateTime auditTime;

    @TableField("ship_time")
    private LocalDateTime shipTime;

    @TableField("deliver_time")
    private LocalDateTime deliverTime;

    @TableField("complete_time")
    private LocalDateTime completeTime;

    @TableField("close_time")
    private LocalDateTime closeTime;

    @TableField("close_reason")
    private String closeReason;

    private String remark;
}
