package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求参数 DTO。
 *
 * <p>前端在 Access Token 即将过期时，使用 Refresh Token 请求新的 Access Token，
 * 实现用户无感刷新，避免频繁重新登录。
 *
 * <p>所属业务领域：用户域 / 认证中心
 *
 * @see com.colonel.saas.auth.controller.AuthController#refresh
 * @see com.colonel.saas.auth.dto.RefreshResponse
 */
@Data
@Schema(description = "刷新令牌请求")
public class RefreshRequest {

    /**
     * Refresh Token，登录时由系统签发。
     * <p>校验规则：不能为空。
     * <p>系统会校验 Token 的签名、过期时间、类型（必须为 "refresh"），
     * 并检查是否已被吊销（Redis 黑名单）。
     */
    @Schema(description = "Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
