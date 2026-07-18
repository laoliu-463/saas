package com.colonel.saas.dto.sample;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 合作详情编辑请求。
 */
public record SampleCooperationUpdateRequest(
        @NotNull Integer version,
        @Size(max = 200) String remark,
        @Size(max = 100) String recipientName,
        @Size(max = 32) String recipientPhone,
        @Size(max = 512) String recipientAddress) {
}
