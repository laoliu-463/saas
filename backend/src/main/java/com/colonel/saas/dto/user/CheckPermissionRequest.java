package com.colonel.saas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "当前用户操作权限检查请求")
public record CheckPermissionRequest(
        @Schema(description = "资源域，例如 product/sample/talent")
        @NotBlank(message = "资源域不能为空")
        @Size(max = 64, message = "资源域不能超过64字符")
        String resource,

        @Schema(description = "操作，例如 view/create/audit/export")
        @NotBlank(message = "操作不能为空")
        @Size(max = 64, message = "操作不能超过64字符")
        String action
) {
}
