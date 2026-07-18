package com.colonel.saas.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** 将核心 Schema 契约接入 Spring Boot readiness health group。 */
@Component("schema")
public class SchemaCompatibilityHealthIndicator implements HealthIndicator {

    private final SchemaCompatibilityProbe probe;

    public SchemaCompatibilityHealthIndicator(SchemaCompatibilityProbe probe) {
        this.probe = probe;
    }

    @Override
    public Health health() {
        SchemaCompatibilityProbe.SchemaCheck check = probe.check();
        Health.Builder builder = check.compatible() ? Health.up() : Health.down();
        return builder.withDetails(check.details()).build();
    }
}
