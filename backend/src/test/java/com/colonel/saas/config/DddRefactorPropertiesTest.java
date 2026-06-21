package com.colonel.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-BASE-001 safety switch default value regression tests.
 */
class DddRefactorPropertiesTest {

    @Test
    void defaultFlagsShouldAllBeFalse() {
        DddRefactorProperties properties = new DddRefactorProperties();

        assertAllFlagsFalse(properties);
    }

    @Test
    void explicitTruePropertiesShouldBindToAllFlags() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("ddd.refactor.enabled", "true")
                .withProperty("ddd.refactor.user-facade.enabled", "true")
                .withProperty("ddd.refactor.config-facade.enabled", "true")
                .withProperty("ddd.refactor.product-facade.enabled", "true")
                .withProperty("ddd.refactor.talent-facade.enabled", "true")
                .withProperty("ddd.refactor.sample-application.enabled", "true")
                .withProperty("ddd.refactor.order-application.enabled", "true")
                .withProperty("ddd.refactor.order-attribution.enabled", "true")
                .withProperty("ddd.refactor.order-amount-policy.enabled", "true")
                .withProperty("ddd.refactor.performance-calc.enabled", "true")
                .withProperty("ddd.refactor.performance-query.enabled", "true")
                .withProperty("ddd.refactor.analytics-shadow.enabled", "true")
                .withProperty("ddd.refactor.outbox.enabled", "true")
                .withProperty("ddd.refactor.data-scope-policy.enabled", "true")
                .withProperty("ddd.refactor.sample-homework-event.enabled", "true")
                .withProperty("ddd.refactor.colonel-partner-contact.enabled", "true");

        DddRefactorProperties properties = Binder.get(environment)
                .bind("ddd.refactor", DddRefactorProperties.class)
                .get();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getUserFacade().isEnabled()).isTrue();
        assertThat(properties.getConfigFacade().isEnabled()).isTrue();
        assertThat(properties.getProductFacade().isEnabled()).isTrue();
        assertThat(properties.getTalentFacade().isEnabled()).isTrue();
        assertThat(properties.getSampleApplication().isEnabled()).isTrue();
        assertThat(properties.getOrderApplication().isEnabled()).isTrue();
        assertThat(properties.getOrderAttribution().isEnabled()).isTrue();
        assertThat(properties.getOrderAmountPolicy().isEnabled()).isTrue();
        assertThat(properties.getPerformanceCalc().isEnabled()).isTrue();
        assertThat(properties.getPerformanceQuery().isEnabled()).isTrue();
        assertThat(properties.getAnalyticsShadow().isEnabled()).isTrue();
        assertThat(properties.getOutbox().isEnabled()).isTrue();
        assertThat(properties.getDataScopePolicy().isEnabled()).isTrue();
        assertThat(properties.getSampleHomeworkEvent().isEnabled()).isTrue();
        assertThat(properties.getColonelPartnerContact().isEnabled()).isTrue();
    }

    static void assertAllFlagsFalse(DddRefactorProperties properties) {
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
        assertThat(properties.getDataScopePolicy()).isNotNull();
        assertThat(properties.getDataScopePolicy().isEnabled()).isFalse();
        assertThat(properties.getSampleHomeworkEvent()).isNotNull();
        assertThat(properties.getSampleHomeworkEvent().isEnabled()).isFalse();
        assertThat(properties.getColonelPartnerContact()).isNotNull();
        assertThat(properties.getColonelPartnerContact().isEnabled()).isFalse();
    }
}
