package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登出请求参数 DTO。
 *
 * <p>用户登出时提交的请求体，系统会将提供的令牌吊销（加入 Redis 黑名单），
 * 防止令牌在有效期内被继续使用。
 *
 * <p>所属业务领域：用户域 / 认证中心
 *
 * @see com.colonel.saas.auth.controller.AuthController#logout
 * @see com.colonel.saas.auth.service.AuthService#logout
 */
@Data
@Schema(description = "登出请求")
public class LogoutRequest {

    /**
     * Access Token（可选）。
     * <p>如果提供，系统会将其加入黑名单（尽力吊销）；
     * 即使不提供，登录仍会成功，仅 Refresh Token 被吊销。
     */
    @Schema(description = "Access Token")
    private String accessToken;

    /**
     * Refresh Token（必填）。
     * <p>校验规则：不能为空。
     * <p>系统会将此 Token 加入 Redis 黑名单，使其在剩余有效期内无法再次使用。
     */
    @Schema(description = "Refresh Token")
    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
