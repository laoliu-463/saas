package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "登录响应数据")
public class LoginResponse {
    @Schema(description = "JWT 访问令牌")
    private String token;
    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;
    @Schema(description = "令牌有效期（秒）", example = "7200")
    private Long expiresIn;
    @Schema(description = "用户 ID")
    private UUID userId;
    @Schema(description = "部门 ID")
    private UUID deptId;
    @Schema(description = "数据范围，1=本人，2=本组，3=全部", example = "3")
    private Integer dataScope;
    @Schema(description = "角色编码列表")
    private List<String> roleCodes;
    @Schema(description = "用户名", example = "admin")
    private String username;
    @Schema(description = "真实姓名", example = "系统管理员")
    private String realName;
}
