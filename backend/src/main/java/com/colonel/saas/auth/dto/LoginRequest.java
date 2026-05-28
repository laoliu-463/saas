package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数 DTO。
 *
 * <p>用户通过用户名和密码进行身份认证时提交的请求体。
 * 密码在传输前应由前端进行加密或通过 HTTPS 保护。
 *
 * <p>所属业务领域：用户域 / 认证中心
 *
 * @see com.colonel.saas.auth.controller.AuthController#login
 * @see com.colonel.saas.auth.dto.LoginResponse
 */
@Data
@Schema(description = "登录请求参数")
public class LoginRequest {

    /**
     * 登录用户名，对应 sys_user 表的 username 字段。
     * <p>校验规则：不能为空，最大长度 50 字符。
     */
    @Schema(description = "登录用户名", example = "admin")
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50字符")
    private String username;

    /**
     * 登录密码，明文传输（建议前端加密或使用 HTTPS）。
     * <p>校验规则：不能为空，最大长度 128 字符。
     * <p>后端使用 BCrypt 进行密码比对验证。
     */
    @Schema(description = "登录密码", example = "********")
    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过128字符")
    private String password;
}
