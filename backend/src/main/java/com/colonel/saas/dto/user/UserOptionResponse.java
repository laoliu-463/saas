package com.colonel.saas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "人员主数据下拉项")
public record UserOptionResponse(
        @Schema(description = "用户ID")
        UUID id,

        @Schema(description = "用户名")
        String username,

        @Schema(description = "真实姓名")
        String realName,

        @Schema(description = "部门ID")
        UUID deptId,

        @Schema(description = "角色编码列表")
        List<String> roleCodes
) {
}
