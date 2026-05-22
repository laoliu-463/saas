package com.colonel.saas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.colonel.saas.security.JwtAuthInterceptor;
import com.colonel.saas.security.OperationLogInterceptor;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Objects;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final OperationLogInterceptor operationLogInterceptor;
    private final @NonNull String[] allowedOriginPatterns;

    public WebConfig(
            @NonNull JwtAuthInterceptor jwtAuthInterceptor,
            @NonNull OperationLogInterceptor operationLogInterceptor,
            @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String allowedOriginPatterns) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.operationLogInterceptor = operationLogInterceptor;
        this.allowedOriginPatterns = parseAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(Objects.requireNonNull(jwtAuthInterceptor))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/refresh",
                        "/douyin/webhooks/**",
                        "/error",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/doc.html",
                        "/system/health",
                        "/api/system/health",
                        "/system/env",
                        "/api/system/env"
                );
        registry.addInterceptor(Objects.requireNonNull(operationLogInterceptor))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/error",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/doc.html",
                        "/system/health",
                        "/api/system/health"
                );
    }

    private static String[] parseAllowedOriginPatterns(String rawPatterns) {
        String[] patterns = Arrays.stream(Objects.requireNonNullElse(rawPatterns, "").split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isBlank())
                .toArray(String[]::new);
        if (patterns.length == 0) {
            throw new IllegalArgumentException("CORS allowed origin patterns must not be empty when credentials are enabled");
        }
        for (String pattern : patterns) {
            if (isUnsafeWildcardOriginPattern(pattern)) {
                throw new IllegalArgumentException("CORS allowed origin patterns must not contain wildcard origin '*' when credentials are enabled");
            }
        }
        return patterns;
    }

    private static boolean isUnsafeWildcardOriginPattern(String pattern) {
        return "*".equals(pattern)
                || "http://*".equalsIgnoreCase(pattern)
                || "https://*".equalsIgnoreCase(pattern)
                || "http://*:*".equalsIgnoreCase(pattern)
                || "https://*:*".equalsIgnoreCase(pattern);
    }
}
