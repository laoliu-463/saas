package com.colonel.saas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 读取当前容器和数据库的可核验版本身份。
 *
 * <p>Git SHA 与镜像 digest 由唯一发布控制器注入；数据库版本必须从数据库事实表读取，
 * 不接受部署参数冒充运行事实。</p>
 */
@Component
public class RuntimeVersionProvider {

    private static final String LEGACY_MIGRATION_SQL = """
            SELECT version || '@' || checksum
            FROM schema_migration_log
            ORDER BY applied_at DESC
            LIMIT 1
            """;
    private static final String FLYWAY_SQL = """
            SELECT version
            FROM flyway_schema_history
            WHERE success = TRUE
            ORDER BY installed_rank DESC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String gitSha;
    private final String imageDigest;

    public RuntimeVersionProvider(
            JdbcTemplate jdbcTemplate,
            @Value("${APP_GIT_SHA:UNAVAILABLE}") String gitSha,
            @Value("${APP_IMAGE_DIGEST:UNAVAILABLE}") String imageDigest) {
        this.jdbcTemplate = jdbcTemplate;
        this.gitSha = normalizeIdentity(gitSha);
        this.imageDigest = normalizeIdentity(imageDigest);
    }

    public RuntimeVersionSnapshot current() {
        return new RuntimeVersionSnapshot(
                gitSha,
                imageDigest,
                queryVersion(LEGACY_MIGRATION_SQL),
                queryVersion(FLYWAY_SQL)
        );
    }

    public static RuntimeVersionProvider unavailable() {
        return new RuntimeVersionProvider(null, "UNAVAILABLE", "UNAVAILABLE");
    }

    private String queryVersion(String sql) {
        if (jdbcTemplate == null) {
            return "NOT_MANAGED";
        }
        try {
            String value = jdbcTemplate.queryForObject(sql, String.class);
            return StringUtils.hasText(value) ? value.trim() : "NOT_MANAGED";
        } catch (DataAccessException ignored) {
            return "NOT_MANAGED";
        }
    }

    private static String normalizeIdentity(String value) {
        return StringUtils.hasText(value) ? value.trim() : "UNAVAILABLE";
    }

    public record RuntimeVersionSnapshot(
            String gitSha,
            String imageDigest,
            String databaseMigrationVersion,
            String flywayVersion) {
    }
}
