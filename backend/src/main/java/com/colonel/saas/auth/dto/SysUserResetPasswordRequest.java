package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置密码请求 DTO。
 *
 * <p>管理员为指定用户重置密码时提交的请求体。
 * 重置后用户下次登录时若系统配置了强制修改密码，将被要求修改密码。
 * 密码后端使用 BCrypt 加密存储。
 *
 * <p>所属业务领域：用户域 / 用户管理
 *
 * @see com.colonel.saas.auth.service.SysUserService#resetPassword
 */
@Schema(description = "重置密码请求")
public record SysUserResetPasswordRequest(

        /**
         * 新密码，后端使用 BCrypt 加密后存储到 sys_user.password 字段。
         * <p>校验：@NotBlank，长度 6-128 字符。
         * <p>重置后用户可用新密码登录，若系统配置了 forcePasswordChange，
         * 登录后将被强制要求再次修改密码。
         */
        @Schema(description = "新密码", example = "NewPassw0rd!")
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度必须在6到128字符之间")
        String newPassword
) {
}
