package com.colonel.saas.dto.talent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OverrideAssigneeRequest(
        @NotNull(message = "新负责人ID不能为空")
        UUID newUserId,

        @NotBlank(message = "归属覆盖原因不能为空")
        String reason
) {
}
