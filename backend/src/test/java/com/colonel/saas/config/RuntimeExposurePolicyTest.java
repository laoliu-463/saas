package com.colonel.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeExposurePolicyTest {

    @Test
    void shouldBypassAuthentication_shouldAllowDouyinOAuthCallback() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");

        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(environment, "/douyin/oauth/callback")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(environment, "/api/douyin/oauth/callback")).isTrue();
    }

    @Test
    void shouldBypassAuthentication_shouldKeepDocsAndSystemEnvPublicOutsideProdOnly() {
        MockEnvironment realPre = new MockEnvironment();
        realPre.setActiveProfiles("real-pre");
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/v3/api-docs")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/swagger-ui/index.html")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/system/env")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/api/system/env")).isTrue();

        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(prod, "/v3/api-docs")).isFalse();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(prod, "/swagger-ui/index.html")).isFalse();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(prod, "/system/env")).isFalse();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(prod, "/api/system/env")).isFalse();
    }

    @Test
    void shouldBypassAuthentication_shouldKeepHealthAndOAuthCallbackPublicInProd() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(prod, "/api/system/health")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(prod, "/system/health")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(prod, "/douyin/oauth/callback")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(prod, "/api/douyin/oauth/callback")).isTrue();
    }

    @Test
    void shouldBypassAuthentication_shouldRejectBlankAndUnknownPaths() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");

        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(environment, null)).isFalse();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(environment, " ")).isFalse();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(environment, "/orders/sync")).isFalse();
    }
}
