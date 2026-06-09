package com.colonel.saas.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-BASE-001 安全开关默认值回归测试。
 *
 * <p>本测试不依赖 Spring 上下文，直接对 {@link DddRefactorProperties} 的字段默认值做断言。
 * 一旦默认值在配置类或 YAML 中被改为 {@code true}，本测试必须报错，避免有人误开线上重构开关。</p>
 */
class DddRefactorPropertiesTest {

    @Test
    void defaultFlagsShouldAllBeFalse() {
        DddRefactorProperties properties = new DddRefactorProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getUserScope()).isNotNull();
        assertThat(properties.getUserScope().isEnabled()).isFalse();
        assertThat(properties.getOrderSync()).isNotNull();
        assertThat(properties.getOrderSync().isEnabled()).isFalse();
        assertThat(properties.getOrderAttribution()).isNotNull();
        assertThat(properties.getOrderAttribution().isEnabled()).isFalse();
        assertThat(properties.getPerformanceCalc()).isNotNull();
        assertThat(properties.getPerformanceCalc().isEnabled()).isFalse();
        assertThat(properties.getProductDisplay()).isNotNull();
        assertThat(properties.getProductDisplay().isEnabled()).isFalse();
        assertThat(properties.getSamplePolicy()).isNotNull();
        assertThat(properties.getSamplePolicy().isEnabled()).isFalse();
        assertThat(properties.getAnalytics()).isNotNull();
        assertThat(properties.getAnalytics().isShadow()).isFalse();
    }

    @Test
    void nestedSubFlagsShouldDefaultToFalseAfterConstruct() {
        DddRefactorProperties.UserScope userScope = new DddRefactorProperties.UserScope();
        DddRefactorProperties.OrderSync orderSync = new DddRefactorProperties.OrderSync();
        DddRefactorProperties.OrderAttribution orderAttribution = new DddRefactorProperties.OrderAttribution();
        DddRefactorProperties.PerformanceCalc performanceCalc = new DddRefactorProperties.PerformanceCalc();
        DddRefactorProperties.ProductDisplay productDisplay = new DddRefactorProperties.ProductDisplay();
        DddRefactorProperties.SamplePolicy samplePolicy = new DddRefactorProperties.SamplePolicy();

        assertThat(userScope.isEnabled()).isFalse();
        assertThat(orderSync.isEnabled()).isFalse();
        assertThat(orderAttribution.isEnabled()).isFalse();
        assertThat(performanceCalc.isEnabled()).isFalse();
        assertThat(productDisplay.isEnabled()).isFalse();
        assertThat(samplePolicy.isEnabled()).isFalse();

        DddRefactorProperties.Analytics analytics = new DddRefactorProperties.Analytics();
        assertThat(analytics.isShadow()).isFalse();
    }
}
