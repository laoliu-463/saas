package com.colonel.saas.config;

import com.colonel.saas.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-BASE-001 配置加载测试。
 * <p>
 * 验证 {@link DddRefactorProperties} 被 Spring Boot 成功加载并正确解析了配置文件（如 application.yml），
 * 且在没有任何额外配置的情况下，所有开关默认值为 false。
 * </p>
 */
class DddRefactorPropertiesIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DddRefactorProperties properties;

    @Test
    void springContextShouldLoadPropertiesWithAllFalse() {
        assertThat(properties).isNotNull();
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
}
