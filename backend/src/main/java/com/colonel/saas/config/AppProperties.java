package com.colonel.saas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralized application properties.
 * All environment-specific switches live here so service layer
 * code does not scatter {@code @Value} annotations.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private TestConfig test = new TestConfig();
    private String dbName;

    @Data
    public static class TestConfig {
        /** True = test/mock mode; False = real SDK mode. */
        private boolean enabled = false;
        /** Seed test data on startup (admin accounts, sample data). */
        private boolean seedOnStartup = false;
        /** Enable mock Douyin API responses. */
        private boolean douyin = false;
        /** Enable mock promotion links. */
        private boolean promotion = false;
        /** Enable mock order sync. */
        private boolean order = false;
        /** Enable mock talent data. */
        private boolean talent = false;
        /** Enable mock logistics data. */
        private boolean logistics = false;
    }
}
