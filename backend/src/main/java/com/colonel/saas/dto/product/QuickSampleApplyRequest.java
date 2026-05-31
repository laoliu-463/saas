package com.colonel.saas.dto.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 快速寄样申请请求 DTO。
 * <p>
 * 用于对一个或多个达人批量发起寄样申请，可指定 SKU、规格、数量、备注和收货地址。
 * 如果未填写收货信息，系统将使用达人默认收货地址。
 * 关联业务领域：商品域（Product）、寄样域（Sample）。
 * </p>
 */
@Data
public class QuickSampleApplyRequest {

    /** 达人 ID 列表，至少包含一个达人 */
    @NotEmpty(message = "talentIds 不能为空")
    private List<@NotBlank String> talentIds;

    /** SKU ID，最大 64 字符 */
    @Size(max = 64, message = "skuId 不能超过 64 字符")
    private String skuId;

    /** 规格描述，最大 128 字符 */
    @Size(max = 128, message = "specification 不能超过 128 字符")
    private String specification;

    /** 寄样数量，范围 1-100，默认 1 */
    @Min(value = 1, message = "quantity 至少为 1")
    @Max(value = 100, message = "quantity 不能超过 100")
    private Integer quantity = 1;

    /** 备注信息，最大 512 字符 */
    @Size(max = 512, message = "remark 不能超过 512 字符")
    private String remark;

    /** 收货人姓名，最大 100 字符 */
    @Size(max = 100, message = "recipientName 不能超过 100 字符")
    private String recipientName;

    /** 收货人电话，最大 32 字符 */
    @Size(max = 32, message = "recipientPhone 不能超过 32 字符")
    private String recipientPhone;

    /** 收货地址，最大 512 字符 */
    @Size(max = 512, message = "recipientAddress 不能超过 512 字符")
    private String recipientAddress;

    /** 渠道人员用户 ID（管理员代选渠道时必填），用于指定寄样归属的渠道人员 */
    private UUID channelUserId;
}
