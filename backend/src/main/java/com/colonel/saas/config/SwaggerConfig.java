package com.colonel.saas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
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
                .build();
    }

    @Bean
    public GroupedOpenApi sdkDebugApi() {
        return GroupedOpenApi.builder()
                .group("sdk-debug")
                .pathsToMatch("/douyin/**")
                .build();
    }
}
