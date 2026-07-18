package com.colonel.saas.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 运行时公开路径暴露策略。
 * <p>
 * 根据当前激活的 Spring Profile（尤其是是否为受保护环境），动态决定哪些 HTTP 路径
 * 可以无需认证即可访问。非受保护环境允许访问 Swagger 文档和系统环境信息端点，
 * 而 real-pre 会将这些路径保护起来，防止信息泄露。
 * </p>
 *
 * <p>路径分为三组：</p>
 * <ul>
 *   <li><strong>BASE_PUBLIC_PATTERNS</strong> —— 基础公开路径，所有环境均可访问（登录、登出、Webhook 回调、健康检查等）</li>
 *   <li><strong>DOC_PUBLIC_PATTERNS</strong> —— Swagger/OpenAPI 文档路径，仅非受保护环境开放</li>
 *   <li><strong>ENV_PUBLIC_PATTERNS</strong> —— 系统环境信息端点，仅非受保护环境开放</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link SecurityConfig} —— Spring Security 配置中引用本类的公开路径列表</li>
 *   <li>{@link LogisticsProperties} —— 快递100回调路径在 BASE_PUBLIC_PATTERNS 中注册</li>
 * </ul>
 *
 * @see SecurityConfig
 */
public final class RuntimeExposurePolicy {

    private static final Set<String> PROTECTED_PROFILES = Set.of("real-pre");

    /**
     * 基础公开路径：所有环境均可访问。
     * <p>包含认证入口（登录/登出/刷新）、抖音 Webhook 回调、快递100回调、错误页和健康检查。</p>
     */
    private static final List<String> BASE_PUBLIC_PATTERNS = List.of(
            "/auth/login",
            "/auth/refresh",
            "/auth/logout",
            "/api/auth/logout",
            "/douyin/webhooks/**",
            "/douyin/oauth/callback",
            "/api/douyin/oauth/callback",
            "/public/logistics/kuaidi100/callback",
            "/api/public/logistics/kuaidi100/callback",
            "/error",
            "/system/health",
            "/api/system/health",
            "/actuator/health/liveness",
            "/api/actuator/health/liveness",
            "/actuator/health/readiness",
            "/api/actuator/health/readiness"
    );

    /** 文档公开路径：Swagger/OpenAPI 相关路径，仅 test 环境开放 */
    private static final List<String> DOC_PUBLIC_PATTERNS = List.of(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/doc.html"
    );

    /** 环境信息公开路径：系统环境信息端点，仅 test 环境开放 */
    private static final List<String> ENV_PUBLIC_PATTERNS = List.of(
            "/system/env",
            "/api/system/env"
    );

    /** 工具类，禁止实例化 */
    private RuntimeExposurePolicy() {
    }

    /**
     * 判断当前是否处于受保护环境 Profile。
     * <p>real-pre 会命中，用于统一保护 Swagger 和系统环境信息端点。</p>
     *
     * @param environment Spring 环境对象，可为 null
     * @return 如果存在受保护 Profile 则返回 true
     */
    public static boolean isProtectedProfileActive(Environment environment) {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .filter(StringUtils::hasText)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(PROTECTED_PROFILES::contains);
    }

    /**
     * 判断系统环境信息端点是否需要管理员权限。
     * <p>受保护环境下需要管理员权限，非受保护环境允许公开访问。</p>
     *
     * @param environment Spring 环境对象
     * @return 受保护环境下返回 true，否则返回 false
     */
    public static boolean requiresAdminForSystemEnv(Environment environment) {
        return isProtectedProfileActive(environment);
    }

    /**
     * 获取当前环境下的所有公开（免认证）安全路径列表。
     * <p>
     * 基础公开路径在所有环境下均生效；文档和环境信息路径仅在非受保护环境下加入。
     * </p>
     *
     * @param environment Spring 环境对象
     * @return 公开路径数组，可直接用于 Spring Security 的 permitAll 配置
     */
    public static String[] publicSecurityPatterns(Environment environment) {
        List<String> patterns = new ArrayList<>(BASE_PUBLIC_PATTERNS);
        if (!isProtectedProfileActive(environment)) {
            patterns.addAll(DOC_PUBLIC_PATTERNS);
            patterns.addAll(ENV_PUBLIC_PATTERNS);
        }
        return patterns.toArray(String[]::new);
    }

    /**
     * 判断给定请求路径是否应绕过认证。
     * <p>
     * 将路径与当前环境的公开路径列表逐一比较，支持 {@code /**} 通配符模式。
     * 路径为空或空白时返回 false（不绕过认证）。
     * </p>
     *
     * @param environment Spring 环境对象
     * @param path        请求路径
     * @return 如果路径匹配任一公开模式则返回 true
     */
    public static boolean shouldBypassAuthentication(Environment environment, String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String normalizedPath = path.trim();
        for (String pattern : publicSecurityPatterns(environment)) {
            if (matches(pattern, normalizedPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单的通配符路径匹配。
     * <p>
     * 支持两种模式：
     * <ul>
     *   <li>以 {@code /**} 结尾：匹配该前缀下的所有子路径（如 {@code /douyin/webhooks/**} 匹配 {@code /douyin/webhooks/order}）</li>
     *   <li>精确匹配：路径必须完全相等</li>
     * </ul>
     * </p>
     *
     * @param pattern 路径模式（可能包含 /** 通配符）
     * @param path    实际请求路径
     * @return 如果匹配则返回 true
     */
    private static boolean matches(String pattern, String path) {
        if (!StringUtils.hasText(pattern)) {
            return false;
        }
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return path.equals(pattern);
    }
}
