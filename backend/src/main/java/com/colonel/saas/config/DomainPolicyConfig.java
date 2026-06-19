package com.colonel.saas.config;

import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root for pure domain policies that still need Spring wiring at
 * application edges.
 */
@Configuration
public class DomainPolicyConfig {

    @Bean
    public ProductDisplayPolicy productDisplayPolicy() {
        return new ProductDisplayPolicy();
    }
}
