package com.colonel.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealProdEnvironmentGuardTest {

    @Test
    void validate_shouldPassForTestProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        RealProdEnvironmentGuard guard = guard(
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
    void validate_shouldRejectRealPreProfileWhenWebhookVerifyDisabled() {
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
                environment,
                false,
                false,
                false,
                "real-pre-secret",
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
    void validate_shouldRejectRealPreProfileWhenJwtSecretIsDefaultPlaceholder() {
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
                environment,
                false,
                false,
                true,
                "test-secret-key-replace-before-real-pre-runtime",
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
    void validate_shouldRejectRealPreCombinedWithAnyOtherProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre", "test");
        RealProdEnvironmentGuard guard = guard(
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
    void validate_shouldRejectProtectedProfileWhenAppTestEnabled() {
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
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
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
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
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
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
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
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
    void validate_shouldRejectRealPreProfileWhenCorsPatternIsBareWildcard() {
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
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

    @Test
    void validate_shouldRejectProtectedProfileWhenOrderSyncDisabled() {
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com",
                false,
                "kuaidi100",
                false
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("order.sync.enabled=true");
    }

    @Test
    void validate_shouldRejectProtectedProfileWhenLogisticsProviderIsMock() {
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com",
                true,
                "mock",
                false
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("logistics.provider=mock");
    }

    @Test
    void validate_shouldRejectProtectedProfileWhenExclusiveEnabled() {
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com",
                true,
                "kuaidi100",
                true
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exclusive.enabled=true");
    }

    @Test
    void validate_shouldRejectProtectedProfileWhenPublicPageCrawlerEnabled() {
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com",
                true,
                "kuaidi100",
                false,
                "api",
                true
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("talent.data.public-page-crawl-enabled=true");
    }

    @Test
    void validate_shouldRejectProtectedProfileWhenTalentCollectModeUsesCrawlerFallback() {
        MockEnvironment environment = realPreEnvironment();
        RealProdEnvironmentGuard guard = guard(
                environment,
                false,
                false,
                true,
                "super-long-real-secret",
                "appid",
                "client-key",
                "client-secret",
                "https://saas.example.com",
                true,
                "kuaidi100",
                false,
                "api_then_crawler",
                false
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not allow non-public talent crawler mode");
    }

    private static MockEnvironment realPreEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");
        return environment;
    }

    private static RealProdEnvironmentGuard guard(
            MockEnvironment environment,
            boolean appTestEnabled,
            boolean douyinTestEnabled,
            boolean verifyWebhookSign,
            String jwtSecret,
            String douyinAppId,
            String douyinClientKey,
            String douyinClientSecret,
            String corsAllowedOriginPatterns) {
        return guard(
                environment,
                appTestEnabled,
                douyinTestEnabled,
                verifyWebhookSign,
                jwtSecret,
                douyinAppId,
                douyinClientKey,
                douyinClientSecret,
                corsAllowedOriginPatterns,
                true,
                "kuaidi100",
                false,
                "api",
                false
        );
    }

    private static RealProdEnvironmentGuard guard(
            MockEnvironment environment,
            boolean appTestEnabled,
            boolean douyinTestEnabled,
            boolean verifyWebhookSign,
            String jwtSecret,
            String douyinAppId,
            String douyinClientKey,
            String douyinClientSecret,
            String corsAllowedOriginPatterns,
            boolean orderSyncEnabled,
            String logisticsProvider,
            boolean exclusiveEnabled) {
        return guard(
                environment,
                appTestEnabled,
                douyinTestEnabled,
                verifyWebhookSign,
                jwtSecret,
                douyinAppId,
                douyinClientKey,
                douyinClientSecret,
                corsAllowedOriginPatterns,
                orderSyncEnabled,
                logisticsProvider,
                exclusiveEnabled,
                "api",
                false
        );
    }

    private static RealProdEnvironmentGuard guard(
            MockEnvironment environment,
            boolean appTestEnabled,
            boolean douyinTestEnabled,
            boolean verifyWebhookSign,
            String jwtSecret,
            String douyinAppId,
            String douyinClientKey,
            String douyinClientSecret,
            String corsAllowedOriginPatterns,
            boolean orderSyncEnabled,
            String logisticsProvider,
            boolean exclusiveEnabled,
            String talentCollectMode,
            boolean talentPublicPageCrawlEnabled) {
        return new RealProdEnvironmentGuard(
                environment,
                appTestEnabled,
                douyinTestEnabled,
                verifyWebhookSign,
                jwtSecret,
                douyinAppId,
                douyinClientKey,
                douyinClientSecret,
                corsAllowedOriginPatterns,
                "redis-secret",
                "db-secret",
                orderSyncEnabled,
                logisticsProvider,
                exclusiveEnabled,
                talentCollectMode,
                talentPublicPageCrawlEnabled
        );
    }
}
