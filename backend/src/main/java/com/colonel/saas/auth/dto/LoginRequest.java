package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "登录请求参数")
public class LoginRequest {

    @Schema(description = "登录用户名", example = "admin")
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50字符")
    private String username;

    @Schema(description = "登录密码", example = "admin123")
    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过128字符")
    private String password;
}
