package com.colonel.saas.security;

import com.colonel.saas.auth.service.AuthService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/auth/login",
            "/auth/refresh",
            "/douyin/webhooks/",
            "/error",
            "/v3/api-docs/",
            "/swagger-ui/",
            "/swagger-resources/",
            "/doc.html",
            "/system/health",
            "/api/system/health",
            "/system/env",
            "/api/system/env"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            AuthService authService,
            ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String requestUri = request.getRequestURI();
        String path = (servletPath != null && !servletPath.isBlank()) ? servletPath : requestUri;
        if (path == null || path.isBlank()) {
            return false;
        }
        final String normalizedPath = path;
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(prefix -> normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response, "缺少或非法的 Authorization 头，请使用格式：Authorization: Bearer <token>");
            return;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Token 不能为空");
            return;
        }

        try {
            Claims claims = jwtTokenProvider.parseClaims(token);

            String tokenType = claims.get("type", String.class);
            if (tokenType != null && !"access".equals(tokenType)) {
                writeUnauthorized(response, "Token 类型错误，请使用 access token");
                return;
            }

            String tokenHash = jwtTokenProvider.getTokenHash(token);
            if (authService.isTokenBlacklisted(tokenHash)) {
                writeUnauthorized(response, "Token 已吊销");
                return;
            }

            UUID userId = UUID.fromString(claims.getSubject());

            Object deptIdRaw = claims.get("deptId");
            UUID deptId = (deptIdRaw == null || deptIdRaw.toString().isBlank())
                    ? null
                    : UUID.fromString(deptIdRaw.toString());

            Integer dataScopeCode = claims.get("dataScope", Integer.class);
            DataScope dataScope = DataScope.fromCode(dataScopeCode == null ? 1 : dataScopeCode);

            List<String> roleCodes = claims.get("roleCodes", List.class);
            String username = claims.get("username", String.class);

            // Set SecurityContext for Spring Security .authenticated() check
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Set request attributes for controller compatibility
            request.setAttribute("userId", userId);
            request.setAttribute("deptId", deptId);
            request.setAttribute("dataScope", dataScope);
            request.setAttribute("roleCodes", roleCodes);
            request.setAttribute("username", username);

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            writeUnauthorized(response, "Token 无效或已过期");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResult<Void> result = ApiResult.of(ResultCode.UNAUTHORIZED.getCode(), msg, null);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
