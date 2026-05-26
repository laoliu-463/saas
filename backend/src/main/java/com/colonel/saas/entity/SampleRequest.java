package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import com.colonel.saas.common.typehandler.JsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sample_request", autoResultMap = true)
public class SampleRequest extends VersionedEntity {

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

    @TableField("dept_id")
    private UUID deptId;

    @TableField("channel_user_id")
    private UUID channelUserId;

    @TableField("channel_dept_id")
    private UUID channelDeptId;

    @TableField("expected_sample_num")
    private Integer expectedSampleNum;

    @TableField("actual_sample_num")
    private Integer actualSampleNum;

    @TableField("tracking_no")
    private String trackingNo;

    @TableField("shipper_code")
    private String shipperCode;

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

    @TableField("recipient_name")
    private String recipientName;

    @TableField("recipient_phone")
    private String recipientPhone;

    @TableField("recipient_address")
    private String recipientAddress;

    @TableField(value = "extra_data", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> extraData;

    @TableField("apply_source")
    private String applySource;

    @TableField("external_apply_id")
    private String externalApplyId;

    @TableField("external_status")
    private String externalStatus;

    @TableField("external_error_code")
    private String externalErrorCode;

    @TableField("external_error_message")
    private String externalErrorMessage;

    @TableField(value = "external_raw_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> externalRawPayload;

    @TableField("logistics_status")
    private String logisticsStatus;

    @TableField("logistics_status_name")
    private String logisticsStatusName;

    @TableField("logistics_last_query_at")
    private LocalDateTime logisticsLastQueryAt;

    @TableField("logistics_last_error")
    private String logisticsLastError;

    @TableField(value = "logistics_raw_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> logisticsRawPayload;

    @TableField("signed_at")
    private LocalDateTime signedAt;

    @TableField("logistics_provider")
    private String logisticsProvider;

    @TableField("logistics_subscribe_status")
    private String logisticsSubscribeStatus;

    @TableField("logistics_subscribed_at")
    private LocalDateTime logisticsSubscribedAt;

    @TableField("logistics_last_subscribe_at")
    private LocalDateTime logisticsLastSubscribeAt;

    @TableField("logistics_last_callback_at")
    private LocalDateTime logisticsLastCallbackAt;

    @TableField("logistics_callback_status")
    private String logisticsCallbackStatus;

    @TableField("logistics_callback_message")
    private String logisticsCallbackMessage;

    @TableField("logistics_exception_reason")
    private String logisticsExceptionReason;

    @TableField("external_last_sync_at")
    private LocalDateTime externalLastSyncAt;
}
