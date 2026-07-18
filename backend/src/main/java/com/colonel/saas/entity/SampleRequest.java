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

/**
 * 寄样申请实体类，映射数据库 {@code sample_request} 表。
 * <p>
 * 该实体属于寄样域，记录达人申请寄样的完整生命周期，
 * 包括申请、审核、发货、签收、完成或关闭等各阶段的状态和数据。
 * 同时存储外部平台（抖店）的同步数据和物流跟踪信息。
 * </p>
 * <p>
 * 继承 {@link VersionedEntity}，支持乐观锁控制。
 * 使用 {@link JsonbTypeHandler} 处理 JSONB 类型字段的序列化/反序列化。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sample_request", autoResultMap = true)
public class SampleRequest extends VersionedEntity {

    /** 寄样申请单号，系统生成的唯一业务编号 */
    @TableField("request_no")
    private String requestNo;

    /** 达人 ID，关联 talent 表主键 */
    @TableField("talent_id")
    private UUID talentId;

    /** 达人在抖店平台的 UID */
    @TableField("talent_uid")
    private String talentUid;

    /** 达人昵称，冗余存储便于列表展示，避免关联查询 */
    @TableField("talent_nickname")
    private String talentNickname;

    /** 达人粉丝数量，用于审核决策参考 */
    @TableField("talent_fans_count")
    private Long talentFansCount;

    /** 达人信用分，用于风险评估和审核决策 */
    @TableField("talent_credit_score")
    private BigDecimal talentCreditScore;

    /** 达人主营类目（如"美妆""服饰""食品"等） */
    @TableField("talent_main_category")
    private String talentMainCategory;

    /** 关联的商品 ID */
    @TableField("product_id")
    private UUID productId;

    /** 申请时关联的活动商品外部 ID，保留活动商品事实。 */
    @TableField("activity_product_id")
    private String activityProductId;

    /** 申请时关联的活动 ID。 */
    @TableField("activity_id")
    private String activityId;

    /** 创建该寄样申请的运营用户 ID */
    @TableField("user_id")
    private UUID userId;

    /** 运营用户所属部门 ID */
    @TableField("dept_id")
    private UUID deptId;

    /** 渠道用户 ID，标识渠道归属 */
    @TableField("channel_user_id")
    private UUID channelUserId;

    /** 渠道部门 ID，标识渠道部门归属 */
    @TableField("channel_dept_id")
    private UUID channelDeptId;

    /** 期望寄样数量 */
    @TableField("expected_sample_num")
    private Integer expectedSampleNum;

    /** 实际发货寄样数量（审核通过后实际发出的数量） */
    @TableField("actual_sample_num")
    private Integer actualSampleNum;

    /** 快递单号/物流跟踪号 */
    @TableField("tracking_no")
    private String trackingNo;

    /** 快递公司编码（如 SF=顺丰、YD=韵达 等） */
    @TableField("shipper_code")
    private String shipperCode;

    /**
     * 寄样申请状态。
     * 例如：待审核、已审核、已发货、已签收、已完成、已关闭等。
     */
    private Integer status;

    /** 拒绝原因，审核不通过时填写 */
    @TableField("reject_reason")
    private String rejectReason;

    /** 审核时间 */
    @TableField("audit_time")
    private LocalDateTime auditTime;

    /** 发货时间 */
    @TableField("ship_time")
    private LocalDateTime shipTime;

    /** 签收时间（达人确认签收或物流系统回调） */
    @TableField("deliver_time")
    private LocalDateTime deliverTime;

    /** 完成时间（寄样流程全部完成） */
    @TableField("complete_time")
    private LocalDateTime completeTime;

    /** 关闭时间（申请被取消或超时关闭） */
    @TableField("close_time")
    private LocalDateTime closeTime;

    /** 关闭原因（如"达人取消""超时未处理"等） */
    @TableField("close_reason")
    private String closeReason;

    /** 备注信息 */
    private String remark;

    /** 收件人姓名 */
    @TableField("recipient_name")
    private String recipientName;

    /** 收件人手机号 */
    @TableField("recipient_phone")
    private String recipientPhone;

    /** 收件人地址 */
    @TableField("recipient_address")
    private String recipientAddress;

    /**
     * 扩展数据，JSONB 格式存储额外的寄样相关信息。
     * 用于存储不属于固定字段的灵活扩展数据。
     */
    @TableField(value = "extra_data", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> extraData;

    /** 申请来源（如"manual"=手动创建、"external"=外部平台同步） */
    @TableField("apply_source")
    private String applySource;

    /** 外部平台的申请 ID，用于与抖店等外部系统关联 */
    @TableField("external_apply_id")
    private String externalApplyId;

    /** 外部平台返回的状态 */
    @TableField("external_status")
    private String externalStatus;

    /** 外部平台返回的错误码 */
    @TableField("external_error_code")
    private String externalErrorCode;

    /** 外部平台返回的错误消息 */
    @TableField("external_error_message")
    private String externalErrorMessage;

    /** 外部平台返回的原始响应数据，JSONB 格式存储，便于调试和问题排查 */
    @TableField(value = "external_raw_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> externalRawPayload;

    /** 物流状态（如 in_transit / delivered / exception 等） */
    @TableField("logistics_status")
    private String logisticsStatus;

    /** 物流状态的中文描述（如"运输中""已签收""异常"等） */
    @TableField("logistics_status_name")
    private String logisticsStatusName;

    /** 最近一次物流查询时间 */
    @TableField("logistics_last_query_at")
    private LocalDateTime logisticsLastQueryAt;

    /** 最近一次物流查询的错误信息（查询失败时记录） */
    @TableField("logistics_last_error")
    private String logisticsLastError;

    /** 物流查询原始响应数据，JSONB 格式存储 */
    @TableField(value = "logistics_raw_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> logisticsRawPayload;

    /** 签收时间（物流系统回调确认的签收时间） */
    @TableField("signed_at")
    private LocalDateTime signedAt;

    /** 物流服务商名称（如"顺丰速运""中通快递"等） */
    @TableField("logistics_provider")
    private String logisticsProvider;

    /** 物流订阅状态（如 subscribed / failed / pending 等） */
    @TableField("logistics_subscribe_status")
    private String logisticsSubscribeStatus;

    /** 首次订阅物流服务的时间 */
    @TableField("logistics_subscribed_at")
    private LocalDateTime logisticsSubscribedAt;

    /** 最近一次物流订阅/续订时间 */
    @TableField("logistics_last_subscribe_at")
    private LocalDateTime logisticsLastSubscribeAt;

    /** 最近一次收到物流回调的时间 */
    @TableField("logistics_last_callback_at")
    private LocalDateTime logisticsLastCallbackAt;

    /** 物流回调状态 */
    @TableField("logistics_callback_status")
    private String logisticsCallbackStatus;

    /** 物流回调消息内容 */
    @TableField("logistics_callback_message")
    private String logisticsCallbackMessage;

    /** 物流异常原因（如"地址不详""收件人拒收"等） */
    @TableField("logistics_exception_reason")
    private String logisticsExceptionReason;

    /** 最近一次与外部平台同步的时间 */
    @TableField("external_last_sync_at")
    private LocalDateTime externalLastSyncAt;
}
