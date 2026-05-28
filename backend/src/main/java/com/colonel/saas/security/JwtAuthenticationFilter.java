package com.colonel.saas.security;

import com.colonel.saas.auth.service.AuthService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.config.RuntimeExposurePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
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

/**
 * JWT 认证过滤器，位于 Spring Security 过滤器链中，负责在请求进入控制器之前完成 JWT 身份认证。
 *
 * <p>架构角色：
 * <ul>
 *   <li>继承 {@link OncePerRequestFilter}，保证每个 HTTP 请求只执行一次过滤逻辑。</li>
 *   <li>是 Spring Security 过滤器链的一环（而非 Spring MVC 拦截器），
 *       因此在 {@code SecurityFilterChain} 配置中注册。</li>
 *   <li>是运行时唯一注册的 JWT 认证入口，负责统一解析 Token、写入请求身份属性并设置
 *       {@link SecurityContextHolder}。</li>
 * </ul>
 *
 * <p>处理流程：
 * <ol>
 *   <li>根据 {@link RuntimeExposurePolicy} 判断当前路径是否需要跳过认证（如公开接口）</li>
 *   <li>提取 Authorization 请求头中的 Bearer Token</li>
 *   <li>调用 {@link JwtTokenProvider} 解析并校验 Token 签名和过期时间</li>
 *   <li>校验 Token 类型必须为 "access"（拒绝 refresh token 被用于接口认证）</li>
 *   <li>校验 Token 是否已被吊销（通过 {@link AuthService#isTokenBlacklisted} 查黑名单）</li>
 *   <li>提取用户身份信息（userId、deptId、dataScope、roleCodes、username）写入请求属性</li>
 *   <li>对"待激活"用户施加受限访问策略（{@link PendingActivationAccessPolicy}）</li>
 *   <li>设置 Spring Security 的 {@link SecurityContextHolder}，使后续 {@code .authenticated()} 校验通过</li>
 * </ol>
 *
 * <p>与其他组件的关系：
 * <ul>
 *   <li>{@link JwtTokenProvider} — Token 解析与验证的核心工具</li>
 *   <li>{@link AuthService} — 黑名单/吊销校验服务</li>
 *   <li>{@link PendingActivationAccessPolicy} — 待激活用户的访问控制策略</li>
 *   <li>{@link RuntimeExposurePolicy} — 运行时暴露策略，决定哪些路径可跳过认证</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 令牌提供者，用于解析和校验 Token */
    private final JwtTokenProvider jwtTokenProvider;

    /** 认证服务，提供 Token 黑名单校验能力 */
    private final AuthService authService;

    /** JSON 序列化器，用于将错误响应写入 HTTP 输出流 */
    private final ObjectMapper objectMapper;

    /** Spring 环境对象，用于读取运行时暴露策略配置 */
    private final Environment environment;

    /**
     * 主构造函数，通过 Spring 依赖注入创建过滤器实例。
     *
     * @param jwtTokenProvider JWT 令牌提供者
     * @param authService      认证服务
     * @param objectMapper     JSON 序列化器
     * @param environment      Spring 环境（用于判断路径是否跳过认证）
     */
    @Autowired
    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            AuthService authService,
            ObjectMapper objectMapper,
            Environment environment) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    /**
     * 测试用简化构造函数，使用默认的 {@link StandardEnvironment}。
     * <p>单元测试中可省略 Environment 注入。
     *
     * @param jwtTokenProvider JWT 令牌提供者
     * @param authService      认证服务
     * @param objectMapper     JSON 序列化器
     */
    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            AuthService authService,
            ObjectMapper objectMapper) {
        this(jwtTokenProvider, authService, objectMapper, new StandardEnvironment());
    }

    /**
     * 判断当前请求是否应跳过 JWT 认证过滤。
     *
     * <p>优先使用 servletPath（考虑 context-path 的情况），回退到 requestURI。
     * 委托给 {@link RuntimeExposurePolicy} 根据运行时配置判断，支持 Swagger、
     * 健康检查、OAuth 回调等公开路径的免认证。
     *
     * @param request 当前 HTTP 请求
     * @return {@code true} 表示跳过过滤（免认证路径），{@code false} 表示需要认证
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String requestUri = request.getRequestURI();
        // 优先取 servletPath，当其为空时回退到完整 requestURI
        String path = (servletPath != null && !servletPath.isBlank()) ? servletPath : requestUri;
        if (path == null || path.isBlank()) {
            return false;  // 路径无法确定时不跳过，强制走认证
        }
        return RuntimeExposurePolicy.shouldBypassAuthentication(environment, path);
    }

    /**
     * 核心过滤逻辑：提取、校验 JWT，并将用户身份信息写入请求上下文。
     *
     * <p>完整处理流程：
     * <ol>
     *   <li>放行 OPTIONS 预检请求（CORS 预检不需要认证）</li>
     *   <li>提取 Authorization 请求头，校验 "Bearer " 前缀格式</li>
     *   <li>解析 JWT Claims，校验签名和过期时间</li>
     *   <li>校验 Token 类型必须为 "access"（防止 refresh token 被滥用）</li>
     *   <li>查询 Token 黑名单，确认未被吊销</li>
     *   <li>提取 userId、deptId、dataScope、roleCodes、username 等身份信息</li>
     *   <li>待激活用户受限访问校验</li>
     *   <li>设置 Spring SecurityContext（使后续 {@code .authenticated()} 检查通过）</li>
     *   <li>将身份信息写入 request attribute，供控制器层读取</li>
     * </ol>
     *
     * @param request     当前 HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链，校验通过后继续执行后续过滤器
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常（写响应体时可能发生）
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 第一步：放行 CORS 预检请求，OPTIONS 方法不携带认证信息
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 第二步：提取并校验 Authorization 请求头格式
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response, "缺少或非法的 Authorization 头，请使用格式：Authorization: Bearer <token>");
            return;
        }

        // 第三步：提取纯 Token 字符串（去掉 "Bearer " 前缀）
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Token 不能为空");
            return;
        }

        try {
            // 第四步：解析 JWT，同时验证签名和过期时间
            Claims claims = jwtTokenProvider.parseClaims(token);

            // 第五步：校验 Token 类型，只允许 access 类型用于接口认证
            String tokenType = claims.get("type", String.class);
            if (tokenType != null && !"access".equals(tokenType)) {
                writeUnauthorized(response, "Token 类型错误，请使用 access token");
                return;
            }

            // 第六步：检查 Token 是否已被吊销（黑名单校验）
            String tokenHash = jwtTokenProvider.getTokenHash(token);
            if (authService.isTokenBlacklisted(tokenHash)) {
                writeUnauthorized(response, "Token 已吊销");
                return;
            }

            // 第七步：从 Claims 中提取用户身份信息
            UUID userId = UUID.fromString(claims.getSubject());

            // 部门 ID 可能为 null（无部门的用户），需要做空值和空字符串双重判断
            Object deptIdRaw = claims.get("deptId");
            UUID deptId = (deptIdRaw == null || deptIdRaw.toString().isBlank())
                    ? null
                    : UUID.fromString(deptIdRaw.toString());

            // 数据范围编码转换为枚举，缺失时默认为 1（个人范围）
            Integer dataScopeCode = claims.get("dataScope", Integer.class);
            DataScope dataScope = DataScope.fromCode(dataScopeCode == null ? 1 : dataScopeCode);

            List<String> roleCodes = claims.get("roleCodes", List.class);
            String username = claims.get("username", String.class);
            Boolean pendingActivation = claims.get("pendingActivation", Boolean.class);

            // 第八步：待激活用户受限访问校验
            // 新用户首次登录后处于 pendingActivation 状态，仅允许查看个人信息和修改密码
            if (Boolean.TRUE.equals(pendingActivation)) {
                String servletPath = request.getServletPath();
                String requestUri = request.getRequestURI();
                String path = (servletPath != null && !servletPath.isBlank()) ? servletPath : requestUri;
                if (!PendingActivationAccessPolicy.isAllowed(request.getMethod(), path)) {
                    writeForbidden(response, "账号待激活，请先修改密码后再访问业务功能");
                    return;
                }
            }

            // 第九步：设置 Spring Security 上下文，使 .authenticated() 校验通过
            // 使用空的 GrantedAuthority 列表，实际权限由数据范围和角色编码在业务层控制
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 第十步：将身份信息写入 request attribute，供控制器层通过 @RequestAttribute 读取
            request.setAttribute("userId", userId);
            request.setAttribute("deptId", deptId);
            request.setAttribute("dataScope", dataScope);
            request.setAttribute("roleCodes", roleCodes);
            request.setAttribute("username", username);

            // 认证通过，继续执行后续过滤器和控制器
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // Token 解析失败（签名错误、格式错误、已过期等），统一返回 401
            writeUnauthorized(response, "Token 无效或已过期");
        }
    }

    /**
     * 向客户端写入 401 Unauthorized 错误响应。
     * <p>
     * 响应格式遵循项目统一的 {@link ApiResult} 信封结构，
     * 使用 UTF-8 编码和 JSON 内容类型。
     *
     * @param response HTTP 响应对象
     * @param msg      错误描述信息，面向前端展示
     * @throws IOException 写入响应流失败时抛出
     */
    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);                    // HTTP 401
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());               // UTF-8 编码
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);                  // JSON 内容类型
        ApiResult<Void> result = ApiResult.of(ResultCode.UNAUTHORIZED.getCode(), msg, null);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    /**
     * 向客户端写入 403 Forbidden 错误响应。
     * <p>
     * 用于待激活用户尝试访问受限业务功能时的响应。
     *
     * @param response HTTP 响应对象
     * @param msg      错误描述信息，面向前端展示
     * @throws IOException 写入响应流失败时抛出
     */
    private void writeForbidden(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);                      // HTTP 403
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());               // UTF-8 编码
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);                  // JSON 内容类型
        ApiResult<Void> result = ApiResult.of(ResultCode.FORBIDDEN.getCode(), msg, null);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
