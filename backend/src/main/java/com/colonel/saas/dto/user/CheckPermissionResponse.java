package com.colonel.saas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 当前用户操作权限检查结果 DTO。
 * <p>
 * 返回权限校验结果，包含资源域、操作类型以及是否允许执行。
 * 关联业务领域：用户域（User）。
 * </p>
 */
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
