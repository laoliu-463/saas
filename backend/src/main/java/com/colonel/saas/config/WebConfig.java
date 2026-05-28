package com.colonel.saas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.colonel.saas.security.OperationLogInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Objects;

/**
 * Web MVC 全局配置。
 * <p>
 * 实现 {@link WebMvcConfigurer} 接口，负责配置：
 * <ul>
 *   <li><strong>CORS 跨域策略</strong> —— 允许前端（Vue 开发服务器或 Nginx 反代）跨域调用后端 API</li>
 *   <li><strong>请求拦截器链</strong> —— 注册操作日志拦截器；JWT 认证由 Spring Security 过滤器链统一处理</li>
 * </ul>
 *
 * <p>CORS 安全策略：</p>
 * <ul>
 *   <li>启用 {@code allowCredentials=true}，允许携带 Cookie/Authorization 头</li>
 *   <li>因此<strong>禁止使用通配符 {@code *} 作为 allowedOrigin</strong>（浏览器安全规范要求）</li>
 *   <li>启动时校验 CORS 配置，若发现不安全的通配符模式则直接拒绝启动</li>
 *   <li>默认允许 {@code http://localhost:*} 和 {@code http://127.0.0.1:*}，生产环境需配置实际域名</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link SecurityConfig} —— 配置 SecurityFilterChain，本类配置 MVC 层面的审计拦截器和 CORS</li>
 *   <li>{@link RuntimeExposurePolicy} —— 提供公开路径列表，用于排除 JWT 认证和操作日志拦截</li>
 *   <li>{@link OperationLogInterceptor} —— 记录操作日志的拦截器</li>
 * </ul>
 *
 * @see SecurityConfig
 * @see RuntimeExposurePolicy
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 操作日志拦截器，记录用户的关键操作（登录、数据变更等）用于审计 */
    private final OperationLogInterceptor operationLogInterceptor;

    /** 解析后的 CORS 允许来源模式数组 */
    private final @NonNull String[] allowedOriginPatterns;
    /** Spring 环境对象，用于获取当前激活的 Profile 以确定公开路径列表 */
    private final Environment environment;

    /**
     * 主构造函数，由 Spring 容器自动注入依赖。
     *
     * @param operationLogInterceptor  操作日志拦截器，记录用户的关键操作用于审计
     * @param allowedOriginPatterns    逗号分隔的 CORS 允许来源模式字符串
     * @param environment              Spring 环境对象，用于确定公开路径列表
     */
    @Autowired
    public WebConfig(
            @NonNull OperationLogInterceptor operationLogInterceptor,
            @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String allowedOriginPatterns,
            Environment environment) {
        this.operationLogInterceptor = operationLogInterceptor;
        // 解析并校验 CORS 来源模式，拒绝不安全的通配符
        this.allowedOriginPatterns = parseAllowedOriginPatterns(allowedOriginPatterns);
        this.environment = environment;
    }

    /**
     * 便捷构造函数，用于测试场景。
     * <p>
     * 自动创建默认的 {@link StandardEnvironment}，避免测试中需要手动传入。
     * </p>
     *
     * @param operationLogInterceptor 操作日志拦截器，记录用户的关键操作用于审计
     * @param allowedOriginPatterns   逗号分隔的 CORS 允许来源模式字符串
     */
    public WebConfig(
            @NonNull OperationLogInterceptor operationLogInterceptor,
            String allowedOriginPatterns) {
        this(operationLogInterceptor, allowedOriginPatterns, new StandardEnvironment());
    }

    /**
     * 配置 CORS 跨域映射规则。
     * <p>
     * 允许配置的来源域名（支持通配符模式如 {@code http://localhost:*}）进行跨域请求，
     * 支持所有常用 HTTP 方法，预检请求缓存 1 小时（3600 秒）。
     * </p>
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                // 使用解析后的安全来源模式（已排除通配符 *）
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                // 允许携带凭证（Cookie、Authorization 头等）
                .allowCredentials(true)
                // 预检请求缓存 1 小时，减少 OPTIONS 请求次数
                .maxAge(3600);
    }

    /**
     * 注册请求拦截器。
     * <p>
     * 仅注册 {@link OperationLogInterceptor}。JWT 认证和身份属性写入由
     * Spring Security 过滤器链中的 {@code JwtAuthenticationFilter} 统一完成，避免
     * MVC 拦截器与 Security Filter 重复解析同一个 Token。
     * </p>
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // 注册操作日志拦截器：记录用户操作，排除公开接口
        registry.addInterceptor(Objects.requireNonNull(operationLogInterceptor))
                .addPathPatterns("/**")
                .excludePathPatterns(RuntimeExposurePolicy.publicSecurityPatterns(environment));
    }

    /**
     * 解析并校验 CORS 允许来源模式。
     * <p>
     * 校验规则：
     * <ul>
     *   <li>来源列表不能为空</li>
     *   <li>不能包含不安全的通配符模式（如 {@code *}、{@code http://*}），
     *       因为启用了 allowCredentials，浏览器规范禁止与通配符来源组合</li>
     * </ul>
     * </p>
     *
     * @param rawPatterns 逗号分隔的原始来源模式字符串
     * @return 解析后的来源模式数组
     * @throws IllegalArgumentException 当来源列表为空或包含不安全通配符时
     */
    private static String[] parseAllowedOriginPatterns(String rawPatterns) {
        String[] patterns = Arrays.stream(Objects.requireNonNullElse(rawPatterns, "").split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isBlank())
                .toArray(String[]::new);
        if (patterns.length == 0) {
            throw new IllegalArgumentException("CORS allowed origin patterns must not be empty when credentials are enabled");
        }
        // 校验是否存在不安全的通配符模式
        for (String pattern : patterns) {
            if (isUnsafeWildcardOriginPattern(pattern)) {
                throw new IllegalArgumentException("CORS allowed origin patterns must not contain wildcard origin '*' when credentials are enabled");
            }
        }
        return patterns;
    }

    /**
     * 判断是否为不安全的 CORS 通配符来源模式。
     * <p>
     * 当允许携带凭证（allowCredentials=true）时，以下模式被浏览器规范禁止：
     * {@code *}、{@code http://*}、{@code https://*}、{@code http://*:*}、{@code https://*:*}。
     * </p>
     *
     * @param pattern 来源模式字符串
     * @return 如果是不安全的通配符模式返回 true
     */
    private static boolean isUnsafeWildcardOriginPattern(String pattern) {
        return "*".equals(pattern)
                || "http://*".equalsIgnoreCase(pattern)
                || "https://*".equalsIgnoreCase(pattern)
                || "http://*:*".equalsIgnoreCase(pattern)
                || "https://*:*".equalsIgnoreCase(pattern);
    }
}
