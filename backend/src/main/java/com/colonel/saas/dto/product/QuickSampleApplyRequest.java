package com.colonel.saas.dto.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QuickSampleApplyRequest {

    @NotEmpty(message = "talentIds 不能为空")
    private List<@NotBlank String> talentIds;

    @Size(max = 64, message = "skuId 不能超过 64 字符")
    private String skuId;

    @Size(max = 128, message = "specification 不能超过 128 字符")
    private String specification;

    @Min(value = 1, message = "quantity 至少为 1")
    @Max(value = 100, message = "quantity 不能超过 100")
    private Integer quantity = 1;

    @Size(max = 512, message = "remark 不能超过 512 字符")
    private String remark;

    @Size(max = 100, message = "recipientName 不能超过 100 字符")
    private String recipientName;

    @Size(max = 32, message = "recipientPhone 不能超过 32 字符")
    private String recipientPhone;

    @Size(max = 512, message = "recipientAddress 不能超过 512 字符")
    private String recipientAddress;
}
