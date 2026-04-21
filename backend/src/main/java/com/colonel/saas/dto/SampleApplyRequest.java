package com.colonel.saas.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SampleApplyRequest {
    @NotBlank(message = "talentId 涓嶈兘涓虹┖")
    private String talentId;

    private String talentNickname;
    private Long talentFansCount;
    private BigDecimal talentCreditScore;
    private String talentMainCategory;

    @NotNull(message = "productId 涓嶈兘涓虹┖")
    private UUID productId;

    @NotNull(message = "quantity 涓嶈兘涓虹┖")
    @Min(value = 1, message = "quantity 鑷冲皯涓?1")
    @Max(value = 100, message = "quantity 涓嶈兘瓒呰繃 100")
    private Integer quantity;

    private String remark;
}

