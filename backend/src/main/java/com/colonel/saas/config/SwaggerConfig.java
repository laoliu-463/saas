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

@Configuration
@Profile("!prod")
public class SwaggerConfig {

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
                    openApi.setTags(openApi.getTags().stream()
                            .filter(tag -> !"抖音联调".equals(tag.getName()))
                            .toList());
                })
                .build();
    }

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
