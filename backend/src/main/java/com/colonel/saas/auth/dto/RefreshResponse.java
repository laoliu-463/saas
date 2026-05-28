package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 刷新令牌响应 DTO。
 *
 * <p>令牌刷新成功后返回给前端的数据，包含新签发的 Access Token
 * 和保持不变的 Refresh Token 及其有效期信息。
 *
 * <p>所属业务领域：用户域 / 认证中心
 *
 * @see com.colonel.saas.auth.controller.AuthController#refresh
 * @see com.colonel.saas.auth.dto.RefreshRequest
 */
@Data
@Builder
@Schema(description = "刷新令牌响应")
public class RefreshResponse {

    /** 新签发的 Access Token，前端需替换旧 Token 使用 */
    @Schema(description = "新的 Access Token")
    private String accessToken;

    /** 新 Access Token 的有效期（秒），默认 7200 秒（2 小时） */
    @Schema(description = "Access Token 有效期（秒）", example = "7200")
    private Long accessTokenExpiresIn;

    /** Refresh Token，刷新操作中保持不变 */
    @Schema(description = "Refresh Token（不变）")
    private String refreshToken;

    /** Refresh Token 的剩余有效期（秒），默认 604800 秒（7 天） */
    @Schema(description = "Refresh Token 有效期（秒）", example = "604800")
    private Long refreshExpiresIn;
}
