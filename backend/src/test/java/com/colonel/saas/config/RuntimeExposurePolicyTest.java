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
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(environment, "/public/logistics/kuaidi100/callback")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(environment, "/api/public/logistics/kuaidi100/callback")).isTrue();
    }

    @Test
    void shouldBypassAuthentication_shouldAllowLogoutInAllProfiles() {
        MockEnvironment realPre = new MockEnvironment();
        realPre.setActiveProfiles("real-pre");

        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/auth/logout")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/api/auth/logout")).isTrue();
    }

    @Test
    void shouldBypassAuthentication_shouldProtectDocsAndSystemEnvInRealPreOnly() {
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");
        MockEnvironment realPre = new MockEnvironment();
        realPre.setActiveProfiles("real-pre");

        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(test, "/v3/api-docs")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(test, "/swagger-ui/index.html")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(test, "/system/env")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(test, "/api/system/env")).isTrue();

        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/v3/api-docs")).isFalse();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/swagger-ui/index.html")).isFalse();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/system/env")).isFalse();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/api/system/env")).isFalse();
    }

    @Test
    void shouldBypassAuthentication_shouldKeepHealthAndOAuthCallbackPublicInRealPre() {
        MockEnvironment realPre = new MockEnvironment();
        realPre.setActiveProfiles("real-pre");

        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/api/system/health")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/system/health")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/api/actuator/health/liveness")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/api/actuator/health/readiness")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/api/actuator/health")).isFalse();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/douyin/oauth/callback")).isTrue();
        assertThat(RuntimeExposurePolicy.shouldBypassAuthentication(realPre, "/api/douyin/oauth/callback")).isTrue();
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
