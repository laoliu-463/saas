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

/**
 * 寄样申请请求 DTO。
 * <p>
 * 用于创建寄样申请，包含达人信息、商品信息、数量及收货地址等。
 * 关联业务领域：寄样域（Sample），涉及达人域（Talent）和商品域（Product）。
 * </p>
 */
@Data
public class SampleApplyRequest {
    /** 达人 ID，必填 */
    @NotBlank(message = "talentId 不能为空")
    private String talentId;

    /** 达人昵称（冗余字段，用于展示） */
    private String talentNickname;
    /** 达人粉丝数（冗余字段，用于展示） */
    private Long talentFansCount;
    /** 达人信用评分（冗余字段，用于展示） */
    private BigDecimal talentCreditScore;
    /** 达人主推类目（冗余字段，用于展示） */
    private String talentMainCategory;

    /** 关联商品 ID，必填 */
    @NotNull(message = "productId 不能为空")
    private UUID productId;

    /** 寄样数量，取值范围 [1, 100]，必填 */
    @NotNull(message = "quantity 不能为空")
    @Min(value = 1, message = "quantity 至少为 1")
    @Max(value = 100, message = "quantity 不能超过 100")
    private Integer quantity;

    /** 收货人姓名，最大 100 字符；兼容前端字段名 receiverName */
    @JsonAlias("receiverName")
    @Size(max = 100, message = "recipientName 不能超过 100 字符")
    private String recipientName;

    /** 收货人电话，最大 32 字符；兼容前端字段名 receiverPhone */
    @JsonAlias("receiverPhone")
    @Size(max = 32, message = "recipientPhone 不能超过 32 字符")
    private String recipientPhone;

    /** 收货地址，最大 512 字符；兼容前端字段名 receiverAddress */
    @JsonAlias("receiverAddress")
    @Size(max = 512, message = "recipientAddress 不能超过 512 字符")
    private String recipientAddress;

    /** 申请来源渠道标识，最大 64 字符 */
    @Size(max = 64, message = "applySource 不能超过 64 字符")
    private String applySource;

    /** 备注信息 */
    private String remark;
}
