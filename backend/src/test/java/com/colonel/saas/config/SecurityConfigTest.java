package com.colonel.saas.config;

import com.colonel.saas.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void securityFilterChain_shouldLoadWithJwtFilterAndPublicMatchers() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SecurityAutoConfiguration.class,
                        SecurityFilterAutoConfiguration.class,
                        WebMvcAutoConfiguration.class))
                .withUserConfiguration(SecurityConfig.class, SecurityTestBeans.class)
                .run(context -> assertThat(context).hasSingleBean(SecurityFilterChain.class));
    }

    @Configuration
    static class SecurityTestBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return mock(JwtAuthenticationFilter.class);
        }
    }
}
