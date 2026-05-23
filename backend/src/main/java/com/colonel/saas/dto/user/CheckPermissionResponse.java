package com.colonel.saas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "当前用户操作权限检查结果")
public record CheckPermissionResponse(
        @Schema(description = "资源域")
        String resource,

        @Schema(description = "操作")
        String action,

        @Schema(description = "是否允许")
        boolean allowed
) {
}
