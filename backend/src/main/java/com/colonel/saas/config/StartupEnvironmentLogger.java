package com.colonel.saas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;

@Component
public class StartupEnvironmentLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupEnvironmentLogger.class);

    private final Environment environment;
    private final boolean appTestEnabled;
    private final boolean douyinTestEnabled;
    private final String dbName;
    private final String datasourceUrl;
    private final String envLabel;

    public StartupEnvironmentLogger(
            Environment environment,
            @Value("${app.test.enabled:false}") boolean appTestEnabled,
            @Value("${douyin.test.enabled:false}") boolean douyinTestEnabled,
            @Value("${DB_NAME:}") String dbName,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${saas.env-label:}") String envLabel) {
        this.environment = environment;
        this.appTestEnabled = appTestEnabled;
        this.douyinTestEnabled = douyinTestEnabled;
        this.dbName = dbName;
        this.datasourceUrl = datasourceUrl;
        this.envLabel = envLabel;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("SAAS environment | activeProfiles={} | envLabel={} | app.test.enabled={} | douyin.test.enabled={} | db.name={}",
                activeProfileLabel(),
                StringUtils.hasText(envLabel) ? envLabel.trim() : "",
                appTestEnabled,
                douyinTestEnabled,
                resolveDatabaseName());
    }

    private String activeProfileLabel() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            activeProfiles = environment.getDefaultProfiles();
        }
        return String.join(",", activeProfiles);
    }

    private String resolveDatabaseName() {
        if (StringUtils.hasText(dbName)) {
            return dbName.trim();
        }
        if (!StringUtils.hasText(datasourceUrl)) {
            return "unknown";
        }
        String normalized = datasourceUrl.trim();
        if (normalized.startsWith("jdbc:")) {
            normalized = normalized.substring("jdbc:".length());
        }
        try {
            String path = URI.create(normalized).getPath();
            if (StringUtils.hasText(path)) {
                return Arrays.stream(path.split("/"))
                        .filter(StringUtils::hasText)
                        .reduce((first, second) -> second)
                        .orElse("unknown");
            }
        } catch (IllegalArgumentException ignored) {
            int slash = normalized.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < normalized.length()) {
                return normalized.substring(slash + 1);
            }
        }
        return "unknown";
    }
}
