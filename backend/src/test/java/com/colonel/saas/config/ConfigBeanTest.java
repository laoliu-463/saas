package com.colonel.saas.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigBeanTest {

    @Test
    void passwordConfigShouldCreateBcryptEncoder() {
        var encoder = new PasswordConfig().passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(encoder.matches("secret", encoder.encode("secret"))).isTrue();
    }

    @Test
    void jacksonConfigShouldUseProjectSerializationDefaults() {
        var mapper = new JacksonConfig().objectMapper(new Jackson2ObjectMapperBuilder());

        assertThat(mapper.getSerializationConfig().isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
        assertThat(mapper.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion())
                .isEqualTo(JsonInclude.Include.NON_NULL);
        assertThat(mapper.getSerializationConfig().getTimeZone().getID()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void mybatisPlusConfigShouldRegisterOptimisticLockAndPaginationInterceptors() {
        MybatisPlusInterceptor interceptor = new MyBatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors()).hasSize(2);
        assertThat(interceptor.getInterceptors())
                .extracting(inner -> inner.getClass().getSimpleName())
                .containsExactly("OptimisticLockerInnerInterceptor", "PaginationInnerInterceptor");
    }

    @Test
    void hutoolHttpConfigShouldInitializeGlobalTimeout() {
        new HutoolHttpConfig().init();

        assertThat(cn.hutool.http.HttpGlobalConfig.getTimeout()).isEqualTo(10_000);
    }

    @Test
    void appPropertiesShouldExposeNestedTestSwitches() {
        AppProperties properties = new AppProperties();
        AppProperties.TestConfig test = new AppProperties.TestConfig();
        test.setEnabled(true);
        test.setSeedOnStartup(true);
        test.setDouyin(true);
        test.setPromotion(true);
        test.setOrder(true);
        test.setTalent(true);
        test.setLogistics(true);

        properties.setDbName("saas_test");
        properties.setTest(test);

        assertThat(properties.getDbName()).isEqualTo("saas_test");
        assertThat(properties.getTest().isEnabled()).isTrue();
        assertThat(properties.getTest().isSeedOnStartup()).isTrue();
        assertThat(properties.getTest().isDouyin()).isTrue();
        assertThat(properties.getTest().isPromotion()).isTrue();
        assertThat(properties.getTest().isOrder()).isTrue();
        assertThat(properties.getTest().isTalent()).isTrue();
        assertThat(properties.getTest().isLogistics()).isTrue();
    }
}
