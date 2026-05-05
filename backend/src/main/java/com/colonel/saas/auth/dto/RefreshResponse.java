package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "刷新令牌响应")
public class RefreshResponse {

    @Schema(description = "新的 Access Token")
    private String accessToken;

    @Schema(description = "Access Token 有效期（秒）", example = "7200")
    private Long accessTokenExpiresIn;

    @Schema(description = "Refresh Token（不变）")
    private String refreshToken;

    @Schema(description = "Refresh Token 有效期（秒）", example = "604800")
    private Long refreshExpiresIn;
}
