package com.colonel.saas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    private final SwaggerConfig config = new SwaggerConfig();

    @Test
    void swaggerConfig_shouldBeProfileNeutral() {
        Profile profile = SwaggerConfig.class.getAnnotation(Profile.class);

        assertThat(profile).isNull();
    }

    @Test
    void openAPI_declaresJwtBearerSecurityAndApiMetadata() {
        OpenAPI openAPI = config.openAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("抖音团长 SaaS API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKey("bearerAuth");
        assertThat(openAPI.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
    }

    @Test
    void businessApi_excludesDouyinTagWhenCustomizerRuns() {
        var businessApi = config.businessApi();
        OpenAPI openAPI = new OpenAPI().tags(List.of(
                new Tag().name("业务接口"),
                new Tag().name("抖音联调")
        ));

        businessApi.getOpenApiCustomizers().forEach(customizer -> customizer.customise(openAPI));

        assertThat(businessApi.getGroup()).isEqualTo("business");
        assertThat(businessApi.getPathsToMatch()).containsExactly("/**");
        assertThat(businessApi.getPathsToExclude()).containsExactly("/douyin/**");
        assertThat(openAPI.getTags()).extracting(Tag::getName).containsExactly("业务接口");
    }

    @Test
    void businessApiCustomizerLeavesNullTagsUntouched() {
        OpenAPI openAPI = new OpenAPI();

        config.businessApi().getOpenApiCustomizers().forEach(customizer -> customizer.customise(openAPI));

        assertThat(openAPI.getTags()).isNull();
    }

    @Test
    void sdkDebugApi_scopesToDouyinPathsAndSetsSdkTag() {
        var sdkDebugApi = config.sdkDebugApi();
        OpenAPI openAPI = new OpenAPI();

        sdkDebugApi.getOpenApiCustomizers().forEach(customizer -> customizer.customise(openAPI));

        assertThat(sdkDebugApi.getGroup()).isEqualTo("sdk-debug");
        assertThat(sdkDebugApi.getPathsToMatch()).containsExactly("/douyin/**");
        assertThat(openAPI.getTags()).singleElement()
                .satisfies(tag -> {
                    assertThat(tag.getName()).isEqualTo("抖音联调");
                    assertThat(tag.getDescription()).contains("真实 SDK 联调");
                });
    }
}
