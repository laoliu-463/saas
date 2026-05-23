package com.colonel.saas.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SampleApplyRequest {
    @NotBlank(message = "talentId 不能为空")
    private String talentId;

    private String talentNickname;
    private Long talentFansCount;
    private BigDecimal talentCreditScore;
    private String talentMainCategory;

    @NotNull(message = "productId 不能为空")
    private UUID productId;

    @NotNull(message = "quantity 不能为空")
    @Min(value = 1, message = "quantity 至少为 1")
    @Max(value = 100, message = "quantity 不能超过 100")
    private Integer quantity;

    @JsonAlias("receiverName")
    @Size(max = 100, message = "recipientName 不能超过 100 字符")
    private String recipientName;

    @JsonAlias("receiverPhone")
    @Size(max = 32, message = "recipientPhone 不能超过 32 字符")
    private String recipientPhone;

    @JsonAlias("receiverAddress")
    @Size(max = 512, message = "recipientAddress 不能超过 512 字符")
    private String recipientAddress;

    @Size(max = 64, message = "applySource 不能超过 64 字符")
    private String applySource;

    private String remark;
}
