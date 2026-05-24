package com.colonel.saas.security;

import com.colonel.saas.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthService authService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, authService, new ObjectMapper());
    }

    @Test
    void publicLoginPath_shouldBypassAuthorizationCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void systemHealthPath_shouldBypassAuthorizationCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void systemEnvPath_shouldRequireAuthorizationWhenProdProfileActive() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        JwtAuthenticationFilter prodFilter =
                new JwtAuthenticationFilter(jwtTokenProvider, authService, new ObjectMapper(), environment);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/env");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        prodFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void systemEnvPath_shouldStayPublicOutsideProdProfile() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/env");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void testToolPath_shouldRequireAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/reset");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void actuatorPath_shouldRequireAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void testToolPath_withValidAccessToken_shouldPopulateRequestAttributes() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("deptId")).thenReturn(deptId.toString());
        when(claims.get("dataScope", Integer.class)).thenReturn(3);
        when(claims.get("roleCodes", List.class)).thenReturn(List.of("admin"));
        when(claims.get("username", String.class)).thenReturn("admin");
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.get("pendingActivation", Boolean.class)).thenReturn(false);
        when(jwtTokenProvider.parseClaims("valid.token")).thenReturn(claims);
        when(jwtTokenProvider.getTokenHash("valid.token")).thenReturn("token-hash");
        when(authService.isTokenBlacklisted("token-hash")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/reset");
        request.addHeader("Authorization", "Bearer valid.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute("userId")).isEqualTo(userId);
        assertThat(request.getAttribute("deptId")).isEqualTo(deptId);
        assertThat(request.getAttribute("username")).isEqualTo("admin");
        assertThat(request.getAttribute("roleCodes")).isEqualTo(List.of("admin"));
    }

    @Test
    void pendingActivationUser_shouldAllowCurrentUserAndPasswordChangeOnly() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("deptId")).thenReturn(deptId.toString());
        when(claims.get("dataScope", Integer.class)).thenReturn(1);
        when(claims.get("roleCodes", List.class)).thenReturn(List.of("biz_staff"));
        when(claims.get("username", String.class)).thenReturn("pending");
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.get("pendingActivation", Boolean.class)).thenReturn(Boolean.TRUE);
        when(jwtTokenProvider.parseClaims("pending.token")).thenReturn(claims);
        when(jwtTokenProvider.getTokenHash("pending.token")).thenReturn("pending-hash");
        when(authService.isTokenBlacklisted("pending-hash")).thenReturn(false);

        MockHttpServletRequest allowed = new MockHttpServletRequest("GET", "/users/current");
        allowed.addHeader("Authorization", "Bearer pending.token");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        MockFilterChain allowedChain = new MockFilterChain();
        filter.doFilter(allowed, allowedResponse, allowedChain);
        assertThat(allowedResponse.getStatus()).isNotEqualTo(401);
        assertThat(allowedResponse.getStatus()).isNotEqualTo(403);
        assertThat(allowedChain.getRequest()).isNotNull();

        MockHttpServletRequest blocked = new MockHttpServletRequest("GET", "/products");
        blocked.addHeader("Authorization", "Bearer pending.token");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedChain = new MockFilterChain();
        filter.doFilter(blocked, blockedResponse, blockedChain);
        assertThat(blockedResponse.getStatus()).isEqualTo(403);
        assertThat(blockedChain.getRequest()).isNull();
    }
}
