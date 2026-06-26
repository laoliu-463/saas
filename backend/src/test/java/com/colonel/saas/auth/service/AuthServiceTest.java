package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.LoginRequest;
import com.colonel.saas.auth.dto.LoginResponse;
import com.colonel.saas.auth.dto.LogoutRequest;
import com.colonel.saas.auth.dto.RefreshRequest;
import com.colonel.saas.auth.dto.RefreshResponse;
import com.colonel.saas.domain.user.application.AuthApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DDD-USER-MIGRATION-015（Issue #24）— AuthService Legacy 委派壳测试。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthApplication authApplication;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authApplication);
    }

    @Test
    void loginDelegatesToApplication() {
        LoginRequest request = new LoginRequest();
        LoginResponse expected = LoginResponse.builder().token("access").build();
        when(authApplication.login(request)).thenReturn(expected);

        authService.login(request);
        verify(authApplication, times(1)).login(request);
    }

    @Test
    void refreshTokenDelegatesToApplication() {
        RefreshRequest request = new RefreshRequest();
        RefreshResponse expected = RefreshResponse.builder().accessToken("access").build();
        when(authApplication.refreshToken(request)).thenReturn(expected);

        authService.refreshToken(request);
        verify(authApplication, times(1)).refreshToken(request);
    }

    @Test
    void logoutDelegatesToApplication() {
        LogoutRequest request = new LogoutRequest();
        authService.logout(request);
        verify(authApplication, times(1)).logout(request);
    }

    @Test
    void isTokenBlacklistedDelegatesToApplication() {
        when(authApplication.isTokenBlacklisted("token")).thenReturn(true);
        authService.isTokenBlacklisted("token");
        verify(authApplication, times(1)).isTokenBlacklisted("token");
    }

    @Test
    void constructorDoesNotInjectMappers() {
        verifyNoInteractions(authApplication);
    }
}
