package com.colonel.saas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Swagger / OpenAPI 3.0 接口文档配置。
 * <p>
 * 通过 {@code @Profile("test")} 限制为仅在 test 环境生效，
 * real-pre 环境不暴露 API 文档，避免安全风险。
 * </p>
 *
 * <p>配置内容：</p>
 * <ul>
 *   <li><strong>全局 OpenAPI 元数据</strong> —— 项目名称、版本、JWT 认证方案声明</li>
 *   <li><strong>business 分组</strong> —— 业务接口（排除 /douyin/** 路径下的 SDK 联调接口）</li>
 *   <li><strong>sdk-debug 分组</strong> —— 抖音 SDK 联调专用接口（仅 /douyin/** 路径）</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link SecurityConfig} —— 安全过滤器链中需要放行 Swagger UI 的路径（由 {@link RuntimeExposurePolicy} 控制）</li>
 *   <li>{@link WebConfig} —— CORS 配置确保前端可通过跨域方式访问接口文档</li>
 * </ul>
 *
 * @see RuntimeExposurePolicy
 */
@Configuration
@Profile("test")
public class SwaggerConfig {

    /**
     * 创建 OpenAPI 全局配置，定义文档标题、版本、认证方案等元数据。
     * <p>
     * 配置 Bearer Token 认证方案（JWT 格式），所有接口在 Swagger UI 中均可携带
     * {@code Authorization: Bearer <token>} 头进行联调测试。
     * </p>
     *
     * @return 配置完成的 OpenAPI 实例
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("项目后端 JWT 鉴权，请按格式传入：Authorization: Bearer <token>")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info()
                        .title("抖音团长 SaaS API")
                        .description("抖音团长 SaaS 系统后端接口文档（含业务接口与 SDK 联调接口）")
                        .version("2.2.0")
                        .contact(new Contact().name("Colonel SaaS Team"))
                        .license(new License().name("Apache 2.0")));
    }

    /**
     * 注册业务接口分组（business），涵盖除抖音 SDK 联调外的所有业务接口。
     * <p>
     * 策略：匹配所有路径（{@code /**}），排除 {@code /douyin/**} 路径，
     * 并在自定义器中移除"抖音联调"标签，确保分组内仅显示纯业务接口。
     * </p>
     *
     * @return 业务接口的 GroupedOpenApi 配置
     */
    @Bean
    public GroupedOpenApi businessApi() {
        return GroupedOpenApi.builder()
                .group("business")
                .pathsToMatch("/**")
                .pathsToExclude("/douyin/**")
                .addOpenApiCustomizer(openApi -> {
                    if (openApi.getTags() == null) {
                        return;
                    }
                    // 移除"抖音联调"标签，避免与 SDK 分组重复
                    openApi.setTags(openApi.getTags().stream()
                            .filter(tag -> !"抖音联调".equals(tag.getName()))
                            .toList());
                })
                .build();
    }

    /**
     * 注册 SDK 联调接口分组（sdk-debug），仅包含抖音相关接口。
     * <p>
     * 匹配 {@code /douyin/**} 路径，并在自定义器中强制设置"抖音联调"标签，
     * 用于真实 SDK 联调、Token 维护、Webhook 回调接收与上游抖音能力验证。
     * </p>
     *
     * @return SDK 联调接口的 GroupedOpenApi 配置
     */
    @Bean
    public GroupedOpenApi sdkDebugApi() {
        return GroupedOpenApi.builder()
                .group("sdk-debug")
                .pathsToMatch("/douyin/**")
                .addOpenApiCustomizer(openApi -> openApi.setTags(List.of(
                        new Tag()
                                .name("抖音联调")
                                .description("用于真实 SDK 联调、Token 维护、Webhook 回调接收与上游抖音能力验证的接口。")
                )))
                .build();
    }
}
