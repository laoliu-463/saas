package com.colonel.saas.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检查应用启动后实际连接的数据库是否满足核心代码契约。
 *
 * <p>该探针只读 information_schema 和 PostgreSQL 分区元数据，不执行任何 DDL。
 * Flyway 负责迁移，探针负责阻止不兼容实例进入 readiness。</p>
 */
@Component
public class SchemaCompatibilityProbe {

    private static final Set<String> REQUIRED_TABLES = Set.of(
            "colonel_activity",
            "colonel_activity_product",
            "colonelsettlement_order",
            "performance_records",
            "pick_source_mapping",
            "product",
            "product_operation_state",
            "product_snapshot",
            "promotion_link"
    );

    private static final Map<String, Set<String>> REQUIRED_COLUMNS = Map.of(
            "colonel_activity", Set.of("activity_status_synced_at"),
            "colonelsettlement_order", Set.of(
                    "channel_attribution_source",
                    "channel_attribution_status",
                    "recruiter_attribution_source",
                    "recruiter_attribution_status"
            ),
            "pick_source_mapping", Set.of("attribution_owner_type"),
            "promotion_link", Set.of("attribution_owner_type")
    );

    private final JdbcTemplate jdbcTemplate;

    public SchemaCompatibilityProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 执行只读 Schema 检查。 */
    public SchemaCheck check() {
        try {
            Set<String> tables = new LinkedHashSet<>(jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = 'public'", String.class));
            Set<String> relevantTables = new LinkedHashSet<>(REQUIRED_TABLES);
            relevantTables.addAll(jdbcTemplate.queryForList(
                    "SELECT child.relname "
                            + "FROM pg_inherits "
                            + "JOIN pg_class child ON child.oid = pg_inherits.inhrelid "
                            + "JOIN pg_class parent ON parent.oid = pg_inherits.inhparent "
                            + "JOIN pg_namespace ns ON ns.oid = parent.relnamespace "
                            + "WHERE ns.nspname = 'public' "
                            + "AND parent.relname = 'colonelsettlement_order'",
                    String.class));

            Map<String, Set<String>> columns = new LinkedHashMap<>();
            jdbcTemplate.queryForList(
                    "SELECT table_name, column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public'")
                    .forEach(row -> columns.computeIfAbsent(String.valueOf(row.get("table_name")),
                                    ignored -> new LinkedHashSet<>())
                            .add(String.valueOf(row.get("column_name"))));

            List<String> missingTables = REQUIRED_TABLES.stream()
                    .filter(table -> !tables.contains(table))
                    .sorted()
                    .toList();
            List<String> missingColumns = new ArrayList<>();
            REQUIRED_COLUMNS.forEach((table, requiredColumns) -> requiredColumns.stream()
                    .filter(column -> !columns.getOrDefault(table, Set.of()).contains(column))
                    .sorted()
                    .forEach(column -> missingColumns.add(table + "." + column)));

            Set<String> partitions = new LinkedHashSet<>(jdbcTemplate.queryForList(
                    "SELECT child.relname "
                            + "FROM pg_inherits "
                            + "JOIN pg_class child ON child.oid = pg_inherits.inhrelid "
                            + "JOIN pg_class parent ON parent.oid = pg_inherits.inhparent "
                            + "JOIN pg_namespace ns ON ns.oid = parent.relnamespace "
                            + "WHERE ns.nspname = 'public' "
                            + "AND parent.relname = 'colonelsettlement_order'",
                    String.class));
            Set<String> orderColumns = REQUIRED_COLUMNS.get("colonelsettlement_order");
            partitions.stream().sorted().forEach(partition -> orderColumns.stream()
                    .filter(column -> !columns.getOrDefault(partition, Set.of()).contains(column))
                    .sorted()
                    .forEach(column -> missingColumns.add(partition + "." + column)));

            boolean flywayHistoryPresent = tables.contains("flyway_schema_history");
            if (!flywayHistoryPresent) {
                missingTables = new ArrayList<>(missingTables);
                missingTables.add("flyway_schema_history");
            }
            return new SchemaCheck(missingTables.isEmpty() && missingColumns.isEmpty(),
                    List.copyOf(missingTables), List.copyOf(missingColumns), null);
        } catch (Exception exception) {
            return new SchemaCheck(false, List.of(), List.of(), exception.getClass().getSimpleName()
                    + ": " + String.valueOf(exception.getMessage()));
        }
    }

    public record SchemaCheck(boolean compatible, List<String> missingTables,
                              List<String> missingColumns, String error) {
        public Map<String, Object> details() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("compatible", compatible);
            details.put("missingTables", missingTables);
            details.put("missingColumns", missingColumns);
            if (error != null) {
                details.put("error", error);
            }
            return details;
        }
    }
}
