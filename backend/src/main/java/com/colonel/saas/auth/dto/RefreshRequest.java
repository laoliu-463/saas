package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "刷新令牌请求")
public class RefreshRequest {

    @Schema(description = "Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
