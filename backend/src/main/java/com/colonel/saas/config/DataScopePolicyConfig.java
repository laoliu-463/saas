package com.colonel.saas.config;

import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataScopePolicyConfig {

    @Bean
    public DataScopePolicy dataScopePolicy() {
        return new DataScopePolicy();
    }

    @Bean
    public DataScopeResolver dataScopeResolver(DataScopePolicy dataScopePolicy) {
        return new DataScopeResolver(dataScopePolicy);
    }
}
