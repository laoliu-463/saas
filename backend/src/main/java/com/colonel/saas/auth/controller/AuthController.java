package com.colonel.saas.auth.controller;

import com.colonel.saas.auth.dto.LoginRequest;
import com.colonel.saas.auth.dto.LoginResponse;
import com.colonel.saas.auth.service.AuthService;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证中心", description = "登录鉴权相关接口")
@RestController
@RequestMapping("/auth")
public class AuthController extends BaseController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回 JWT 令牌与数据范围信息")
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ok(authService.login(request));
    }
}
