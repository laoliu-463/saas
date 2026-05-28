package com.colonel.saas.dto.sample;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 寄样单批量操作请求 DTO。
 * <p>
 * 用于对多个寄样单同时执行相同的状态流转操作（如批量审核通过、批量驳回等）。
 * 接口调用方需在 URL 路径中指定动作（如 {@code /batch/approve}、{@code /batch/reject}），
 * 本 DTO 仅承载待操作的寄样单号列表和统一备注。
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）。
 * </p>
 */
@Data
public class SampleBatchActionRequest {

    /**
     * 寄样单号列表。
     * <p>
     * 每个元素为寄样单的唯一业务编号（如 "SR20250101001"），
     * 最多支持 100 条，不能为空。
     * </p>
     */
    @Schema(description = "寄样单号列表。", example = "[\"SR20250101001\",\"SR20250101002\"]")
    @NotEmpty(message = "requestNos cannot be empty")
    @Size(max = 100, message = "requestNos size cannot exceed 100")
    private List<String> requestNos;

    /**
     * 备注/原因说明。
     * <p>
     * 批量驳回等操作时建议填写统一的驳回原因，
     * 便于达人了解被驳回的原因，也用于操作审计追溯。
     * </p>
     */
    @Schema(description = "备注/原因。批量驳回时必填。", example = "商品缺货")
    private String remark;
}
