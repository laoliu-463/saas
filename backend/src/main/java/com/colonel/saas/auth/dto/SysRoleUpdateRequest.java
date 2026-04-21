package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "更新角色请求")
public record SysRoleUpdateRequest(
        @Schema(description = "角色编码", example = "biz_staff")
        @NotBlank(message = "角色编码不能为空")
        @Size(max = 50, message = "角色编码长度不能超过50字符")
        String roleCode,

        @Schema(description = "角色名称", example = "招商专员")
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 100, message = "角色名称长度不能超过100字符")
        String roleName,

        @Schema(description = "数据范围：1=本人，2=本组，3=全部", example = "1")
        @Min(value = 1, message = "数据范围值非法")
        @Max(value = 3, message = "数据范围值非法")
        Integer dataScope,

        @Schema(description = "状态：1=启用，0=禁用", example = "1")
        @Min(value = 0, message = "状态值非法")
        @Max(value = 1, message = "状态值非法")
        Integer status,

        @Schema(description = "备注")
        @Size(max = 255, message = "备注长度不能超过255字符")
        String remark
) {
}
