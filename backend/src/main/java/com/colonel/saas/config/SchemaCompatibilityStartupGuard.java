package com.colonel.saas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 在 real-pre 默认阻止不兼容 Schema 的应用进入可服务状态。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaCompatibilityStartupGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaCompatibilityStartupGuard.class);

    private final SchemaCompatibilityProbe probe;
    private final boolean failFast;

    public SchemaCompatibilityStartupGuard(
            SchemaCompatibilityProbe probe,
            @Value("${schema.validation.startup-fail-fast:true}") boolean failFast) {
        this.probe = probe;
        this.failFast = failFast;
    }

    @Override
    public void run(ApplicationArguments args) {
        SchemaCompatibilityProbe.SchemaCheck check = probe.check();
        if (check.compatible()) {
            log.info("Core schema compatibility check passed");
            return;
        }
        log.error("Core schema compatibility check failed: {}", check.details());
        if (failFast) {
            throw new IllegalStateException("Core database schema is incompatible: " + check.details());
        }
        log.warn("Schema startup fail-fast is disabled; readiness remains DOWN");
    }
}
