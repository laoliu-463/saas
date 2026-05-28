package com.colonel.saas.security;

import com.colonel.saas.auth.service.AuthService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 旧版 JWT 认证拦截器，作为 Spring MVC HandlerInterceptor 在控制器方法执行前进行 JWT 身份校验。
 *
 * <p>架构角色：
 * <ul>
 *   <li>实现 {@link HandlerInterceptor} 接口，在 {@code preHandle} 阶段完成认证。</li>
 *   <li>生产运行链路不再注册本拦截器，JWT 认证统一由
 *       {@link JwtAuthenticationFilter} 在 Spring Security 过滤器层完成。</li>
 *   <li>认证通过后，将用户身份信息（userId、deptId、dataScope、roleCodes、username）
 *       写入 request attribute，供控制器通过 {@code @RequestAttribute} 注解或直接读取。</li>
 * </ul>
 *
 * <p>处理流程：
 * <ol>
 *   <li>放行 OPTIONS 预检请求</li>
 *   <li>提取并校验 Authorization 请求头的 Bearer Token 格式</li>
 *   <li>调用 {@link JwtTokenProvider} 解析 JWT Claims</li>
 *   <li>校验 Token 类型必须为 "access"</li>
 *   <li>通过 {@link AuthService#isTokenBlacklisted} 校验 Token 是否已吊销</li>
 *   <li>提取身份信息写入 request attribute</li>
 * </ol>
 *
 * <p>注意：本拦截器不处理"待激活"用户的受限访问逻辑，该逻辑由
 * {@link JwtAuthenticationFilter} 在过滤器层处理。
 *
 * <p>与其他组件的关系：
 * <ul>
 *   <li>{@link JwtTokenProvider} — Token 解析和验证</li>
 *   <li>{@link AuthService} — Token 黑名单校验</li>
 *   <li>{@link JwtAuthenticationFilter} — 同等功能在 Spring Security 过滤器层的实现</li>
 * </ul>
 */
@Deprecated(since = "2026-05-27", forRemoval = false)
public class JwtAuthInterceptor implements HandlerInterceptor {

    /** JWT 令牌提供者，用于解析和校验 Token */
    private final JwtTokenProvider jwtTokenProvider;

    /** 认证服务，提供 Token 黑名单校验能力 */
    private final AuthService authService;

    /** JSON 序列化器，用于将错误响应写入 HTTP 输出流 */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，通过 Spring 依赖注入创建拦截器实例。
     *
     * @param jwtTokenProvider JWT 令牌提供者
     * @param authService      认证服务（黑名单校验）
     * @param objectMapper     JSON 序列化器
     */
    public JwtAuthInterceptor(JwtTokenProvider jwtTokenProvider, AuthService authService, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    /**
     * 在控制器方法执行前进行 JWT 认证校验。
     *
     * <p>完整处理流程：
     * <ol>
     *   <li>放行 OPTIONS 预检请求（CORS 预检不需要认证）</li>
     *   <li>提取 Authorization 请求头，校验 "Bearer " 前缀格式</li>
     *   <li>调用 {@link JwtTokenProvider#parseClaims} 解析 JWT 并校验签名与过期时间</li>
     *   <li>校验 Token 类型必须为 "access"（防止 refresh token 被滥用）</li>
     *   <li>通过 {@link AuthService#isTokenBlacklisted} 校验 Token 是否已吊销</li>
     *   <li>从 Claims 提取身份信息写入 request attribute</li>
     * </ol>
     *
     * <p>任何校验失败均返回 HTTP 401 并写入标准 {@link ApiResult} 错误响应体，
     * 同时返回 {@code false} 阻止控制器方法执行。
     *
     * @param request  当前 HTTP 请求
     * @param response HTTP 响应
     * @param handler  请求对应的处理器（通常是 HandlerMethod）
     * @return {@code true} 认证通过，继续执行控制器；{@code false} 认证失败，已写入错误响应
     * @throws Exception 异常情况
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // 放行 CORS 预检请求，OPTIONS 方法不携带认证信息
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 提取并校验 Authorization 请求头格式
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response, "缺少或非法的 Authorization 头，请使用格式：Authorization: Bearer <token>");
            return false;
        }

        // 提取纯 Token 字符串（去掉 "Bearer " 前缀）
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Token 不能为空");
            return false;
        }

        try {
            // 解析 JWT，同时验证签名和过期时间
            Claims claims = jwtTokenProvider.parseClaims(token);

            // 校验 Token 类型：只允许 access 类型用于接口认证
            String tokenType = claims.get("type", String.class);
            if (tokenType != null && !"access".equals(tokenType)) {
                writeUnauthorized(response, "Token 类型错误，请使用 access token");
                return false;
            }

            // 检查 Token 是否已被吊销（黑名单校验）
            String tokenHash = jwtTokenProvider.getTokenHash(token);
            if (authService.isTokenBlacklisted(tokenHash)) {
                writeUnauthorized(response, "Token 已吊销");
                return false;
            }

            // 从 Claims 提取用户身份信息
            UUID userId = UUID.fromString(claims.getSubject());

            // 部门 ID 可能为 null（无部门的用户），需做空值和空字符串双重判断
            Object deptIdRaw = claims.get("deptId");
            UUID deptId = (deptIdRaw == null || deptIdRaw.toString().isBlank())
                    ? null
                    : UUID.fromString(deptIdRaw.toString());

            // 数据范围编码转换为枚举，缺失时默认为 1（个人范围）
            Integer dataScopeCode = claims.get("dataScope", Integer.class);
            DataScope dataScope = DataScope.fromCode(dataScopeCode == null ? 1 : dataScopeCode);

            // 将身份信息写入 request attribute，供控制器通过 @RequestAttribute 注解读取
            request.setAttribute("userId", userId);
            request.setAttribute("deptId", deptId);
            request.setAttribute("dataScope", dataScope);
            request.setAttribute("roleCodes", claims.get("roleCodes", List.class));
            request.setAttribute("username", claims.get("username", String.class));
            return true;
        } catch (Exception e) {
            // Token 解析失败（签名错误、格式错误、已过期等），统一返回 401
            writeUnauthorized(response, "Token 无效或已过期");
            return false;
        }
    }

    /**
     * 向客户端写入 401 Unauthorized 错误响应。
     *
     * <p>响应格式遵循项目统一的 {@link ApiResult} 信封结构，
     * 使用 UTF-8 编码和 JSON 内容类型。
     *
     * @param response HTTP 响应对象
     * @param msg      错误描述信息，面向前端展示
     * @throws Exception 写入响应流或 JSON 序列化失败时抛出
     */
    private void writeUnauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);              // HTTP 401
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());         // UTF-8 编码
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);            // JSON 内容类型
        ApiResult<Void> result = ApiResult.of(ResultCode.UNAUTHORIZED.getCode(), msg, null);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
