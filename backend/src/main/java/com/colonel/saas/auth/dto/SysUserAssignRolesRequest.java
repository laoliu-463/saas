package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "用户分配角色请求")
public record SysUserAssignRolesRequest(
        @Schema(description = "角色 ID 列表")
        @NotNull(message = "角色列表不能为空")
        List<UUID> roleIds
) {
}
