package com.colonel.saas.security;

import com.colonel.saas.auth.service.AuthService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthService authService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private ObjectMapper objectMapper;
    private JwtAuthInterceptor interceptor;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        interceptor = new JwtAuthInterceptor(jwtTokenProvider, authService, objectMapper);
        when(authService.isTokenBlacklisted(any())).thenReturn(false);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    void preHandle_shouldReturnFalseWhenAuthorizationHeaderMissing() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void preHandle_shouldReturnFalseWhenAuthorizationHeaderNotBearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic abc");

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void preHandle_shouldReturnFalseWhenTokenEmpty() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer   ");

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void preHandle_shouldReturnFalseWhenTokenInvalid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");
        when(jwtTokenProvider.parseClaims("invalid.token.here")).thenThrow(new RuntimeException("invalid"));

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void preHandle_shouldSetRequestAttributesAndReturnTrue() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("deptId")).thenReturn(deptId.toString());
        when(claims.get("dataScope", Integer.class)).thenReturn(3);
        when(claims.get("roleCodes", List.class)).thenReturn(List.of("ADMIN"));
        when(claims.get("username", String.class)).thenReturn("testuser");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.token");

        when(jwtTokenProvider.parseClaims("valid.token")).thenReturn(claims);

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        verify(request).setAttribute("userId", userId);
        verify(request).setAttribute("deptId", deptId);
        verify(request).setAttribute("dataScope", DataScope.ALL);
        verify(request).setAttribute("roleCodes", List.of("ADMIN"));
        verify(request).setAttribute("username", "testuser");
    }

    @Test
    void preHandle_shouldHandleNullDeptId() throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("deptId")).thenReturn(null);
        when(claims.get("dataScope", Integer.class)).thenReturn(null);
        when(claims.get("roleCodes", List.class)).thenReturn(null);
        when(claims.get("username", String.class)).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.token");

        when(jwtTokenProvider.parseClaims("valid.token")).thenReturn(claims);

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        verify(request).setAttribute("deptId", null);
        verify(request).setAttribute("dataScope", DataScope.PERSONAL);
    }
}
