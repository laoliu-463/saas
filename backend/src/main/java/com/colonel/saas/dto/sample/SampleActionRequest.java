package com.colonel.saas.dto.sample;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 寄样单状态流转请求 DTO。
 * <p>
 * 用于单个寄样单的状态变更操作，支持审核通过、驳回、发货、签收、交作业、完成、关闭等动作。
 * 通过 {@code action} 字段指定要执行的动作，由后端校验当前状态是否允许该动作。
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）。
 * </p>
 */
@Data
public class SampleActionRequest {

    /**
     * 状态流转动作标识。
     * <p>
     * 可用标准值：PENDING_SHIP（审核通过→待发货）、REJECTED（驳回）、SHIPPING（标记发货）、
     * DELIVERED（标记签收）、PENDING_HOMEWORK（待交作业）、COMPLETED（完成）、CLOSED（关闭）。
     * </p>
     * <p>
     * 兼容上游旧版别名：APPROVED（等同 PENDING_SHIP）、SHIPPED（等同 SHIPPING）、
     * PENDING_TASK（等同 PENDING_HOMEWORK）、FINISHED（等同 COMPLETED）。
     * </p>
     */
    @Schema(description = "状态流转动作。可用值包括 PENDING_SHIP、REJECTED、SHIPPING、DELIVERED、PENDING_HOMEWORK、COMPLETED、CLOSED；兼容值包括 APPROVED、SHIPPED、PENDING_TASK、FINISHED。", example = "SHIPPING")
    @NotBlank(message = "action cannot be empty")
    private String action;

    /**
     * 原因说明。
     * <p>
     * 在驳回（REJECTED）或关闭（CLOSED）等场景下建议填写，用于记录操作原因，
     * 便于后续审计追溯。
     * </p>
     */
    @Schema(description = "原因说明。驳回、关闭等场景建议填写。", example = "顺丰发出")
    private String reason;

    /**
     * 物流单号。
     * <p>
     * 执行发货动作（SHIPPING）时必填，用于记录包裹的快递追踪单号，
     * 同时触发物流信息查询服务。
     * </p>
     */
    @Schema(description = "物流单号。发货时必填。", example = "SF1234567890")
    private String trackingNo;

    /**
     * 快递公司编码。
     * <p>
     * 执行发货动作时选填，例如 "SF"（顺丰）、"YTO"（圆通）等。
     * 用于精确匹配物流查询接口的承运商参数。
     * </p>
     */
    @Schema(description = "快递公司编码。发货时选填，用于物流追踪。", example = "SF")
    private String shipperCode;
}
