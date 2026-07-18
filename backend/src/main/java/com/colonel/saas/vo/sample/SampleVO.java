package com.colonel.saas.vo.sample;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 寄样单详情 VO（Value Object）。
 * <p>
 * 用于寄样单详情页的数据展示，包含寄样单的全量信息，涵盖达人信息、商品信息、
 * 物流信息、操作记录、资质校验结果及需求快照等。是寄样域前端最完整的数据载体。
 * </p>
 * <p>
 * 主要职责：
 * <ul>
 *   <li>承载寄样单详情页所需的全部展示字段</li>
 *   <li>整合达人、商品、物流、审核等多维度数据</li>
 *   <li>提供业务枚举值的中文标签映射（如 applySourceLabel、cooperationTypeLabel）</li>
 *   <li>包含资质校验结果和需求快照的嵌套结构</li>
 * </ul>
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）— 详情视图。
 * </p>
 *
 * @see com.colonel.saas.common.enums.SampleStatus 寄样单状态枚举
 */
@Data
public class SampleVO {

    /** 寄样单唯一标识（UUID）。 */
    private UUID id;

    /** 乐观锁版本号。 */
    private Integer version;

    /**
     * 寄样单业务编号。
     * <p>
     * 面向用户的可读编号，格式通常为 "SR" + 日期 + 序号（如 SR20250101001）。
     * </p>
     */
    private String requestNo;

    /** 关联达人 ID（UUID）。 */
    private UUID talentId;

    /**
     * 达人抖音 UID。
     * <p>
     * 达人在抖音平台的唯一标识，用于跨系统关联达人数据。
     * </p>
     */
    private String talentUid;

    /** 达人昵称或真实姓名。 */
    private String talentName;

    /** 达人粉丝数量，用于评估达人影响力。 */
    private Long talentFansCount;

    /**
     * 达人信用评分。
     * <p>
     * 平台对达人综合信用的评级，用于资质校验和寄样资格判断。
     * </p>
     */
    private String talentCreditScore;

    /** 达人主营类目，如"美妆"、"服饰"、"食品"等。 */
    private String talentMainCategory;

    /** 关联商品 ID（UUID）。 */
    private UUID productId;

    /** 申请活动 ID；历史数据仅从商品快照读取返回，不回写寄样单。 */
    private String activityId;

    /**
     * 商品外部 ID。
     * <p>
     * 商品在抖音电商平台的原始商品 ID（如 num_iid），用于跨系统关联。
     * </p>
     */
    private String productExternalId;

    /** 商品标题/名称。 */
    private String productName;

    /** 申请时的商品规格。 */
    private String productSpecification;

    /** 商品封面图 URL。 */
    private String productCover;

    /**
     * 商品价格文本。
     * <p>
     * 格式化后的价格字符串（如 "¥99.00"），用于前端直接展示。
     * </p>
     */
    private String productPriceText;

    /** 抖店店铺 ID。 */
    private String shopId;

    /** 抖店店铺名称。 */
    private String shopName;

    /** 申请寄样的商品数量。 */
    private Integer quantity;

    /** 申请人用户 ID（UUID）。 */
    private UUID applicantUserId;

    /** 申请人姓名。 */
    private String applicantName;

    /** 渠道运营人员用户 ID（UUID）。 */
    private UUID channelUserId;

    /** 渠道运营人员姓名。 */
    private String channelUserName;

    /** 团长用户 ID（UUID）。 */
    private UUID colonelUserId;

    /** 团长姓名。 */
    private String colonelUserName;

    /** 物流单号（快递面单号），发货前为 null。 */
    private String trackingNo;

    /** 快递公司编码（如 SF、YTO、ZTO），发货前为 null。 */
    private String shipperCode;

    /** 收件人姓名。 */
    private String recipientName;

    /** 收件人电话。 */
    private String recipientPhone;

    /** 收件人详细地址。 */
    private String recipientAddress;

    /** 物流公司名称（中文），如"顺丰速运"。 */
    private String logisticsCompany;

    /**
     * 物流数据来源。
     * <p>
     * 标识物流信息的获取渠道，如 "auto"（自动查询）、"manual"（手动填写）。
     * </p>
     */
    private String logisticsSource;

    /**
     * 物流状态码。
     * <p>
     * 物流查询接口返回的状态标识，如 "0"（在途）、"1"（揽收）、"2"（签收）。
     * </p>
     */
    private String logisticsStatus;

    /**
     * 物流状态中文名称。
     * <p>
     * 对应 logisticsStatus 的可读中文标签，如"在途"、"已签收"。
     * </p>
     */
    private String logisticsStatusName;

    /** 最近一次物流查询的时间。 */
    private LocalDateTime logisticsLastQueryAt;

    /** 最近一次物流查询的错误信息，查询成功时为 null。 */
    private String logisticsLastError;

    /** 达人签收时间。 */
    private LocalDateTime signedAt;

    /** 审核驳回原因，非驳回状态下为 null。 */
    private String rejectReason;

    /** 关闭原因，非关闭状态下为 null。 */
    private String closeReason;

    /** 运营人员备注信息。 */
    private String remark;

    /** 达人申请寄样的理由。 */
    private String applyReason;

    /**
     * 申请来源标识。
     * <p>
     * 记录寄样申请的来源渠道编码，如 "platform"（平台申请）、"external"（外部导入）。
     * </p>
     */
    private String applySource;

    /** 申请来源的中文标签，用于前端展示。 */
    private String applySourceLabel;

    /**
     * 合作类型标识。
     * <p>
     * 寄样合作的业务模式编码，如 "pure_sample"（纯寄样）、"sample_with_video"（寄样+视频）。
     * </p>
     */
    private String cooperationType;

    /** 合作类型的中文标签，用于前端展示。 */
    private String cooperationTypeLabel;

    /**
     * 寄样归属类型标识。
     * <p>
     * 标识寄样商品的所有权归属方式，如 "merchant"（商家提供）、"colonel"（团长提供）。
     * </p>
     */
    private String sampleOwnerType;

    /** 寄样归属类型的中文标签，用于前端展示。 */
    private String sampleOwnerTypeLabel;

    /**
     * 作业类型标识。
     * <p>
     * 达人签收后需要完成的作业任务类型，如 "video"（发布视频）、"live"（直播带货）。
     * </p>
     */
    private String homeworkType;

    /** 作业类型的中文标签，用于前端展示。 */
    private String homeworkTypeLabel;

    /**
     * 资质校验结果。
     * <p>
     * 包含校验是否通过、不通过原因、销售数据等信息的嵌套 Map 结构。
     * 由 {@link SampleEligibilityCheckVO} 转换而来。
     * </p>
     */
    private Map<String, Object> eligibilityCheck;

    /**
     * 需求快照。
     * <p>
     * 寄样申请提交时的需求配置快照，包含合作要求、作业要求等信息，
     * 用于后续校验需求是否变更。
     * </p>
     */
    private Map<String, Object> requirementSnapshot;

    /**
     * 当前状态标识。
     * <p>
     * 寄样单当前所处的状态，对应 {@link com.colonel.saas.common.enums.SampleStatus} 的 API 状态值。
     * </p>
     */
    private String status;

    /** 固定七键、有序的合作台操作能力矩阵。 */
    private Map<String, SampleActionAvailabilityVO> actionAvailability;

    /** 创建时间（寄样申请提交时间）。 */
    private LocalDateTime createTime;

    /** 最近更新时间。 */
    private LocalDateTime updateTime;

    /** 发货时间。 */
    private LocalDateTime shipTime;

    /** 签收时间。 */
    private LocalDateTime deliverTime;

    /** 完成时间（作业完成、流程闭环时间）。 */
    private LocalDateTime completeTime;
}
