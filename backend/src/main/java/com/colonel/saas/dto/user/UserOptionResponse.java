package com.colonel.saas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * 人员主数据下拉项 DTO。
 * <p>
 * 用于前端下拉选择器中展示用户选项，包含用户基础信息和角色列表。
 * 关联业务领域：用户域（User）。
 * </p>
 */
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
        List<String> roleCodes,

        @Schema(description = "渠道短码")
        String channelCode
) {
    public UserOptionResponse(UUID id, String username, String realName, UUID deptId, List<String> roleCodes) {
        this(id, username, realName, deptId, roleCodes, null);
    }
}
