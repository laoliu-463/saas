package com.colonel.saas.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class TalentEnrichModeGuard {

    private final Environment environment;

    @Value("${talent.enrich.mode:real}")
    private String enrichMode;

    public TalentEnrichModeGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) && "test".equalsIgnoreCase(enrichMode)) {
                throw new IllegalStateException("prod profile does not allow talent.enrich.mode=test");
            }
        }
    }
}


