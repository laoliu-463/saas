package com.colonel.saas.dto.sample;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 寄样单批量发货请求 DTO。
 * <p>
 * 用于一次性为多个寄样单填写物流信息并执行发货动作。
 * 每个明细项 {@link SampleBatchShipItem} 对应一个寄样单的发货数据。
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）。
 * </p>
 */
@Data
public class SampleBatchShipRequest {

    /**
     * 批量发货明细列表。
     * <p>
     * 列表中每个元素包含一个寄样单号及其对应的物流单号和快递公司编码。
     * 最多支持 100 条记录，列表不能为空，且每个明细项均需通过字段校验（{@code @Valid}）。
     * </p>
     */
    @Schema(description = "批量发货列表。", example = "[{\"requestNo\":\"SR20250101001\",\"trackingNo\":\"SF1234567890\"}]")
    @NotEmpty(message = "items cannot be empty")
    @Size(max = 100, message = "items size cannot exceed 100")
    @Valid
    private List<SampleBatchShipItem> items;
}
