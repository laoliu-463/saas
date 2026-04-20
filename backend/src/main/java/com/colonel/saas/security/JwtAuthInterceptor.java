package com.colonel.saas.security;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthInterceptor(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response, "缺少或非法的 Authorization 头");
            return false;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Token 不能为空");
            return false;
        }

        try {
            Claims claims = jwtTokenProvider.parseClaims(token);
            UUID userId = UUID.fromString(claims.getSubject());

            Object deptIdRaw = claims.get("deptId");
            UUID deptId = (deptIdRaw == null || deptIdRaw.toString().isBlank())
                    ? null
                    : UUID.fromString(deptIdRaw.toString());

            Integer dataScopeCode = claims.get("dataScope", Integer.class);
            DataScope dataScope = DataScope.fromCode(dataScopeCode == null ? 1 : dataScopeCode);

            request.setAttribute("userId", userId);
            request.setAttribute("deptId", deptId);
            request.setAttribute("dataScope", dataScope);
            request.setAttribute("roleCodes", claims.get("roleCodes", List.class));
            request.setAttribute("username", claims.get("username", String.class));
            return true;
        } catch (Exception e) {
            writeUnauthorized(response, "Token 无效或已过期");
            return false;
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResult<Void> result = ApiResult.of(ResultCode.UNAUTHORIZED.getCode(), msg, null);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
