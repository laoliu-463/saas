package com.colonel.saas.dto.talent;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTalentShippingAddressRequest {

    @Size(max = 100, message = "收件人姓名最多 100 个字符")
    private String recipientName;

    @Size(max = 32, message = "收件人电话最多 32 个字符")
    private String recipientPhone;

    @Size(max = 512, message = "收件地址最多 512 个字符")
    private String recipientAddress;
}
