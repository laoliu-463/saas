package com.colonel.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-BASE-001 Spring configuration binding test.
 */
class DddRefactorPropertiesIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void springContextShouldLoadPropertiesWithAllFalse() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DddRefactorProperties.class);
            DddRefactorProperties properties = context.getBean(DddRefactorProperties.class);
            assertAllFlagsFalse(properties);
        });
    }

    private static void assertAllFlagsFalse(DddRefactorProperties properties) {
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getUserFacade()).isNotNull();
        assertThat(properties.getUserFacade().isEnabled()).isFalse();
        assertThat(properties.getConfigFacade()).isNotNull();
        assertThat(properties.getConfigFacade().isEnabled()).isFalse();
        assertThat(properties.getProductFacade()).isNotNull();
        assertThat(properties.getProductFacade().isEnabled()).isFalse();
        assertThat(properties.getTalentFacade()).isNotNull();
        assertThat(properties.getTalentFacade().isEnabled()).isFalse();
        assertThat(properties.getSampleApplication()).isNotNull();
        assertThat(properties.getSampleApplication().isEnabled()).isFalse();
        assertThat(properties.getOrderApplication()).isNotNull();
        assertThat(properties.getOrderApplication().isEnabled()).isFalse();
        assertThat(properties.getOrderAttribution()).isNotNull();
        assertThat(properties.getOrderAttribution().isEnabled()).isFalse();
        assertThat(properties.getOrderAmountPolicy()).isNotNull();
        assertThat(properties.getOrderAmountPolicy().isEnabled()).isFalse();
        assertThat(properties.getPerformanceCalc()).isNotNull();
        assertThat(properties.getPerformanceCalc().isEnabled()).isFalse();
        assertThat(properties.getPerformanceQuery()).isNotNull();
        assertThat(properties.getPerformanceQuery().isEnabled()).isFalse();
        assertThat(properties.getAnalyticsShadow()).isNotNull();
        assertThat(properties.getAnalyticsShadow().isEnabled()).isFalse();
        assertThat(properties.getOutbox()).isNotNull();
        assertThat(properties.getOutbox().isEnabled()).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DddRefactorProperties.class)
    static class TestConfiguration {
    }
}
