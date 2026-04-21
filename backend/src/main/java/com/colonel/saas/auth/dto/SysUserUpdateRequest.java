package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "更新用户请求")
public record SysUserUpdateRequest(
        @Schema(description = "真实姓名", example = "张三")
        @Size(max = 100, message = "真实姓名长度不能超过100字符")
        String realName,

        @Schema(description = "手机号", example = "13800138000")
        @Size(max = 20, message = "手机号长度不能超过20字符")
        String phone,

        @Schema(description = "邮箱", example = "demo@example.com")
        @Size(max = 100, message = "邮箱长度不能超过100字符")
        String email,

        @Schema(description = "状态：1=启用，0=禁用", example = "1")
        @Min(value = 0, message = "状态值非法")
        @Max(value = 1, message = "状态值非法")
        Integer status
) {
}
