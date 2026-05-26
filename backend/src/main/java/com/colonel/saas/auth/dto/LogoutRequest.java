package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "登出请求")
public class LogoutRequest {

    @Schema(description = "Access Token")
    private String accessToken;

    @Schema(description = "Refresh Token")
    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
