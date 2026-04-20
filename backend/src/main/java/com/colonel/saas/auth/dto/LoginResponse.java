package com.colonel.saas.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String tokenType;
    private Long expiresIn;
    private UUID userId;
    private UUID deptId;
    private Integer dataScope;
    private List<String> roleCodes;
    private String username;
    private String realName;
}
