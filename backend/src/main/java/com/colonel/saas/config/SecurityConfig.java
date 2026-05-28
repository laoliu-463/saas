package com.colonel.saas.config;

import com.colonel.saas.security.JwtAuthenticationFilter;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置。
 * <p>
 * 定义全局 HTTP 安全策略，包括认证方式、会话管理、路径权限和 CORS 配置。
 * 本项目采用 JWT 无状态认证方案，不使用传统的 Session 和表单登录。
 * </p>
 *
 * <p>安全策略要点：</p>
 * <ul>
 *   <li>禁用 CSRF —— REST API 使用 JWT 令牌认证，无需 CSRF 保护</li>
 *   <li>禁用 HTTP Basic 和表单登录 —— 统一使用 JWT 认证</li>
 *   <li>无状态会话 —— 不创建 HttpSession，每次请求都通过 JWT 验证身份</li>
 *   <li>JWT 过滤器置于 {@code UsernamePasswordAuthenticationFilter} 之前执行</li>
 *   <li>公开路径列表由 {@link RuntimeExposurePolicy} 动态确定（根据环境 Profile 不同而变化）</li>
 *   <li>Actuator 端点需要认证，防止未授权访问敏感监控数据</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link JwtAuthenticationFilter} —— JWT 令牌解析和身份认证核心过滤器</li>
 *   <li>{@link RuntimeExposurePolicy} —— 动态决定哪些路径可以匿名访问</li>
 *   <li>{@link WebConfig} —— 配置 CORS 和 MVC 操作日志拦截器</li>
 *   <li>{@link PasswordConfig} —— 提供密码编码器，用于用户注册/登录时的密码处理</li>
 * </ul>
 *
 * @see JwtAuthenticationFilter
 * @see RuntimeExposurePolicy
 * @see WebConfig
 */
@Configuration
public class SecurityConfig {

    /** JWT 认证过滤器，负责从请求头中解析 JWT 令牌并完成身份认证 */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    /** Spring 环境对象，用于获取当前激活的 Profile 以确定公开路径列表 */
    private final Environment environment;

    /**
     * 构造函数，注入 JWT 过滤器和 Spring 环境对象。
     *
     * @param jwtAuthenticationFilter JWT 认证过滤器
     * @param environment             Spring 环境对象
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.environment = environment;
    }

    /**
     * 配置安全过滤器链。
     * <p>
     * 定义完整的 HTTP 安全策略，返回配置好的 {@link SecurityFilterChain}。
     * </p>
     *
     * @param http Spring Security 的 HttpSecurity 构建器
     * @return 配置完成的安全过滤器链
     * @throws Exception 配置过程中可能抛出的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF：REST API 使用 JWT 认证，无需 CSRF Token
                .csrf(AbstractHttpConfigurer::disable)
                // 禁用 HTTP Basic 认证
                .httpBasic(AbstractHttpConfigurer::disable)
                // 禁用表单登录，统一使用 JWT 认证
                .formLogin(AbstractHttpConfigurer::disable)
                // 无状态会话管理：不创建 HttpSession，完全依赖 JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 在 UsernamePasswordAuthenticationFilter 之前插入 JWT 认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 配置 URL 权限规则
                .authorizeHttpRequests(auth -> auth
                        // 公开路径（登录、Webhook 回调、健康检查等）允许匿名访问
                        .requestMatchers(RuntimeExposurePolicy.publicSecurityPatterns(environment)).permitAll()
                        // Actuator 监控端点需要认证，防止未授权访问
                        .requestMatchers("/actuator/**").authenticated()
                        // 其他所有请求都需要认证
                        .anyRequest().authenticated())
                // 启用 CORS，使用 Spring 默认配置（由 WebConfig 中的 CorsRegistry 自定义）
                .cors(Customizer.withDefaults());
        return http.build();
    }
}
