package com.colonel.saas.vo.sample;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 寄样看板卡片 VO（Value Object）。
 * <p>
 * 用于寄样管理看板（Kanban Board）中每张卡片的数据展示，以精简字段呈现
 * 寄样单的核心信息，便于运营人员在看板视图中快速浏览和操作。
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）— 看板视图。
 * </p>
 */
@Data
public class SampleBoardCard {

    /**
     * 寄样单唯一标识（UUID）。
     */
    private UUID id;

    /**
     * 寄样单业务编号。
     * <p>
     * 面向用户的可读编号，如 "SR20250101001"。
     * </p>
     */
    private String requestNo;

    /**
     * 达人名称。
     * <p>
     * 申请寄样的达人昵称或真实姓名，用于看板中快速识别达人身份。
     * </p>
     */
    private String talentName;

    /**
     * 商品 ID（UUID）。
     * <p>
     * 关联的商品在系统内的唯一标识。
     * </p>
     */
    private UUID productId;

    /**
     * 商品名称。
     * <p>
     * 寄样申请对应的商品标题，用于看板中快速识别商品。
     * </p>
     */
    private String productName;

    /**
     * 寄样数量。
     * <p>
     * 申请寄样的商品数量。
     * </p>
     */
    private Integer quantity;

    /**
     * 渠道运营人员名称。
     * <p>
     * 负责该寄样单的内部渠道运营人姓名。
     * </p>
     */
    private String channelUserName;

    /**
     * 物流单号。
     * <p>
     * 发货后填写的快递追踪单号，发货前为 null。
     * </p>
     */
    private String trackingNo;

    /**
     * 驳回原因。
     * <p>
     * 审核驳回时填写的原因说明，非驳回状态下为 null。
     * </p>
     */
    private String rejectReason;

    /**
     * 备注信息。
     * <p>
     * 运营人员添加的通用备注。
     * </p>
     */
    private String remark;

    /**
     * 当前状态标识。
     * <p>
     * 寄样单当前所处的状态，对应 {@link com.colonel.saas.common.enums.SampleStatus} 的 API 状态值。
     * </p>
     */
    private String status;

    /**
     * 创建时间。
     * <p>
     * 寄样申请的提交时间。
     * </p>
     */
    private LocalDateTime createTime;

    /**
     * 进入当前状态的时间。
     * <p>
     * 记录寄样单最近一次状态变更的时间点，用于计算在当前状态的停留时长，
     * 辅助运营人员判断是否需要催办。
     * </p>
     */
    private LocalDateTime stateEnterTime;
}
