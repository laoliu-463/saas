package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "重置密码请求")
public record SysUserResetPasswordRequest(
        @Schema(description = "新密码", example = "NewPassw0rd!")
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度必须在6到128字符之间")
        String newPassword
) {
}
