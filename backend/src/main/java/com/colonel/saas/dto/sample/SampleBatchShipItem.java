package com.colonel.saas.dto.sample;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 批量发货明细项 DTO。
 * <p>
 * 表示批量发货请求中单个寄样单的发货信息，包含寄样单号、物流单号和快递公司编码。
 * 作为 {@link SampleBatchShipRequest} 的列表元素使用。
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）。
 * </p>
 */
@Data
public class SampleBatchShipItem {

    /**
     * 寄样单号。
     * <p>
     * 唯一标识一条寄样申请记录，格式通常为 "SR" + 日期 + 序号（如 SR20250101001）。
     * </p>
     */
    @Schema(description = "寄样单号。", example = "SR20250101001")
    @NotBlank(message = "requestNo is required")
    private String requestNo;

    /**
     * 物流单号（快递面单号）。
     * <p>
     * 快递公司分配的包裹追踪号码，用于物流状态查询。
     * </p>
     */
    @Schema(description = "物流单号。", example = "SF1234567890")
    @NotBlank(message = "trackingNo is required")
    private String trackingNo;

    /**
     * 快递公司编码。
     * <p>
     * 例如 "SF"（顺丰）、"YTO"（圆通）、"ZTO"（中通）等。
     * 用于匹配物流查询接口的承运商参数，发货时必填。
     * </p>
     */
    @Schema(description = "快递公司编码。发货时必填。", example = "SF")
    private String shipperCode;
}
