package com.colonel.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealProdEnvironmentGuardTest {

    @Test
    void validate_shouldPassForLocalMockProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-mock");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                true,
                true,
                false,
                "",
                "",
                "",
                "",
                ""
        );

        assertThatCode(guard::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_shouldRejectRealProfileWhenWebhookVerifyDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                false,
                false,
                "prod-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("real profile requires douyin.webhook.verify-sign=true");
    }

    @Test
    void validate_shouldRejectRealPreProfileWhenWebhookVerifyDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                false,
                false,
                "prod-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("real-pre profile requires douyin.webhook.verify-sign=true");
    }

    @Test
    void validate_shouldRejectProdProfileWhenJwtSecretIsDefaultPlaceholder() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                false,
                true,
                "dev-secret-key-replace-in-production-with-random-64-char-string",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default placeholder security.jwt.secret");
    }

    @Test
    void validate_shouldRejectRealPreCombinedWithLocalMock() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre", "local-mock");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("real-pre profile cannot be combined with local-mock");
    }

    @Test
    void validate_shouldRejectRealPreCombinedWithTest() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre", "test");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("real-pre profile cannot be combined with test");
    }

    @Test
    void validate_shouldRejectProdCombinedWithLocalMock() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "local-mock");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod profile cannot be combined with local-mock");
    }

    @Test
    void validate_shouldRejectProdCombinedWithTest() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "test");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod profile cannot be combined with test");
    }

    @Test
    void validate_shouldRejectProtectedProfileWhenAppTestEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                true,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.test.enabled=true");
    }

    @Test
    void validate_shouldRejectProtectedProfileWhenDouyinTestEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                true,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("douyin.test.enabled=true");
    }

    @Test
    void validate_shouldRejectProtectedProfileWhenDouyinSecretsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "",
                "",
                "",
                "https://saas.example.com"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("douyin.app.app-id");
    }

    @Test
    void validate_shouldPassForProtectedProfileWhenRequiredValuesAreSafe() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com"
        );

        assertThatCode(guard::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_shouldRejectProdProfileWhenCorsPatternIsBareWildcard() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        RealProdEnvironmentGuard guard = new RealProdEnvironmentGuard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "*"
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsafe CORS pattern");
    }
}
