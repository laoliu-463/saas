package com.colonel.saas.dto.sample;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 已发货寄样单的物流公司补录请求。
 *
 * <p>仅用于历史记录缺少快递公司编码时补齐承运商，不允许修改已有运单号。</p>
 */
@Data
public class SampleLogisticsRepairRequest {

    @NotBlank(message = "shipperCode cannot be empty")
    @Schema(description = "快递公司编码，例如 SF、YTO、ZTO。", example = "YTO")
    private String shipperCode;
}
