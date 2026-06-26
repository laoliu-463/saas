package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.LoginRequest;
import com.colonel.saas.auth.dto.LoginResponse;
import com.colonel.saas.auth.dto.LogoutRequest;
import com.colonel.saas.auth.dto.RefreshRequest;
import com.colonel.saas.auth.dto.RefreshResponse;
import com.colonel.saas.domain.user.application.AuthApplication;
import org.springframework.stereotype.Service;

/**
 * 认证核心服务（Legacy 委派壳）。
 *
 * <p>AuthService 的 DDD 化已完成（DDD-USER-MIGRATION-015，Issue #24）。
 * 本类仅保留 4 个 public 签名作为兼容入口，所有实现委派给用户域 DDD 应用服务
 * {@link AuthApplication}。Controller 与其它调用方零改动。</p>
 */
@Service
public class AuthService {

    private final AuthApplication authApplication;

    public AuthService(AuthApplication authApplication) {
        this.authApplication = authApplication;
    }

    public LoginResponse login(LoginRequest request) {
        return authApplication.login(request);
    }

    public RefreshResponse refreshToken(RefreshRequest request) {
        return authApplication.refreshToken(request);
    }

    public void logout(LogoutRequest request) {
        authApplication.logout(request);
    }

    public boolean isTokenBlacklisted(String token) {
        return authApplication.isTokenBlacklisted(token);
    }
}
