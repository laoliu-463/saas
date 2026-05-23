package com.colonel.saas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "当前用户修改密码请求")
public record ChangePasswordRequest(
        @Schema(description = "原密码")
        @NotBlank(message = "原密码不能为空")
        @Size(min = 6, max = 128, message = "原密码长度必须在6到128字符之间")
        String oldPassword,

        @Schema(description = "新密码")
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 128, message = "新密码长度必须在6到128字符之间")
        String newPassword
) {
}
