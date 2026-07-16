package com.colonel.saas.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.colonel.saas.entity.TalentComplaintReminder;
import com.colonel.saas.mapper.TalentComplaintMapper;
import com.colonel.saas.mapper.TalentComplaintReminderMapper;
import com.colonel.saas.testsupport.DockerAvailable;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CooperationWorkbenchActionsSchemaContractTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migrate/V20260716_001__cooperation_workbench_actions.sql");
    private static final Path INIT_DB = Path.of("src/main/resources/db/init-db.sql");
    private static final Path MIGRATE_ALL = Path.of("src/main/resources/db/migrate-all.sql");
    private static final String MIGRATION_RESOURCE =
            "db/migrate/V20260716_001__cooperation_workbench_actions.sql";

    private static final Set<String> ACTION_TABLES = Set.of(
            "sample_private_note",
            "talent_complaint",
            "talent_complaint_attachment",
            "talent_complaint_reminder");
    private static final Set<String> ACTION_INDEXES = Set.of(
            "uk_sample_private_note_owner",
            "idx_talent_complaint_talent_status",
            "uk_talent_complaint_attachment_storage_key",
            "idx_talent_complaint_attachment_complaint",
            "uk_talent_complaint_reminder_recipient",
            "idx_talent_complaint_reminder_recipient_order",
            "idx_talent_complaint_reminder_recipient_unread");
    private static final Set<String> ACTION_FOREIGN_KEYS = Set.of(
            "fk_sample_private_note_request",
            "fk_sample_private_note_user",
            "fk_talent_complaint_request",
            "fk_talent_complaint_talent",
            "fk_talent_complaint_product",
            "fk_talent_complaint_reporter",
            "fk_talent_complaint_attachment_complaint",
            "fk_talent_complaint_reminder_complaint",
            "fk_talent_complaint_reminder_recipient");
    private static final Pattern FOREIGN_KEY_PATTERN = Pattern.compile(
            "^constraint\\s+(\\w+)\\s+foreign key\\s*\\((\\w+)\\)\\s+"
                    + "references\\s+(\\w+)\\s*\\((\\w+)\\)$");

    private static final Map<String, Map<String, String>> REQUIRED_COLUMNS = Map.of(
            "sample_private_note", Map.ofEntries(
                    Map.entry("id", "uuid primary key default gen_random_uuid()"),
                    Map.entry("sample_request_id", "uuid not null"),
                    Map.entry("user_id", "uuid not null"),
                    Map.entry("content", "varchar(200) not null"),
                    Map.entry("version", "integer not null default 0"),
                    Map.entry("deleted", "smallint not null default 0"),
                    Map.entry("create_time", "timestamp not null default current_timestamp"),
                    Map.entry("update_time", "timestamp not null default current_timestamp"),
                    Map.entry("create_by", "uuid"),
                    Map.entry("update_by", "uuid")),
            "talent_complaint", Map.ofEntries(
                    Map.entry("id", "uuid primary key default gen_random_uuid()"),
                    Map.entry("sample_request_id", "uuid not null"),
                    Map.entry("talent_id", "uuid not null"),
                    Map.entry("product_id", "uuid not null"),
                    Map.entry("reporter_user_id", "uuid not null"),
                    Map.entry("reason_code", "varchar(64) not null"),
                    Map.entry("content", "text not null"),
                    Map.entry("status", "varchar(32) not null default 'submitted'"),
                    Map.entry("version", "integer not null default 0"),
                    Map.entry("deleted", "smallint not null default 0"),
                    Map.entry("create_time", "timestamp not null default current_timestamp"),
                    Map.entry("update_time", "timestamp not null default current_timestamp"),
                    Map.entry("create_by", "uuid"),
                    Map.entry("update_by", "uuid")),
            "talent_complaint_attachment", Map.ofEntries(
                    Map.entry("id", "uuid primary key default gen_random_uuid()"),
                    Map.entry("complaint_id", "uuid not null"),
                    Map.entry("storage_key", "varchar(512) not null"),
                    Map.entry("original_name", "varchar(255) not null"),
                    Map.entry("content_type", "varchar(128) not null"),
                    Map.entry("file_size", "bigint not null"),
                    Map.entry("sha256", "char(64) not null"),
                    Map.entry("deleted", "smallint not null default 0"),
                    Map.entry("create_time", "timestamp not null default current_timestamp"),
                    Map.entry("update_time", "timestamp not null default current_timestamp"),
                    Map.entry("create_by", "uuid"),
                    Map.entry("update_by", "uuid")),
            "talent_complaint_reminder", Map.ofEntries(
                    Map.entry("id", "uuid primary key default gen_random_uuid()"),
                    Map.entry("complaint_id", "uuid not null"),
                    Map.entry("recipient_user_id", "uuid not null"),
                    Map.entry("read_at", "timestamp"),
                    Map.entry("version", "integer not null default 0"),
                    Map.entry("deleted", "smallint not null default 0"),
                    Map.entry("create_time", "timestamp not null default current_timestamp"),
                    Map.entry("update_time", "timestamp not null default current_timestamp"),
                    Map.entry("create_by", "uuid"),
                    Map.entry("update_by", "uuid")));

    private static final Map<String, ForeignKeyDefinition> REQUIRED_FOREIGN_KEYS = Map.of(
            "fk_sample_private_note_request",
            new ForeignKeyDefinition("sample_request_id", "sample_request", "id"),
            "fk_sample_private_note_user",
            new ForeignKeyDefinition("user_id", "sys_user", "id"),
            "fk_talent_complaint_request",
            new ForeignKeyDefinition("sample_request_id", "sample_request", "id"),
            "fk_talent_complaint_talent",
            new ForeignKeyDefinition("talent_id", "talent", "id"),
            "fk_talent_complaint_product",
            new ForeignKeyDefinition("product_id", "product", "id"),
            "fk_talent_complaint_reporter",
            new ForeignKeyDefinition("reporter_user_id", "sys_user", "id"),
            "fk_talent_complaint_attachment_complaint",
            new ForeignKeyDefinition("complaint_id", "talent_complaint", "id"),
            "fk_talent_complaint_reminder_complaint",
            new ForeignKeyDefinition("complaint_id", "talent_complaint", "id"),
            "fk_talent_complaint_reminder_recipient",
            new ForeignKeyDefinition("recipient_user_id", "sys_user", "id"));

    private static final Map<String, IndexDefinition> REQUIRED_UNIQUE_INDEXES = Map.of(
            "uk_sample_private_note_owner",
            new IndexDefinition(
                    "sample_private_note",
                    "sample_request_id, user_id",
                    true,
                    "deleted = 0"),
            "uk_talent_complaint_attachment_storage_key",
            new IndexDefinition(
                    "talent_complaint_attachment",
                    "storage_key",
                    true,
                    ""),
            "uk_talent_complaint_reminder_recipient",
            new IndexDefinition(
                    "talent_complaint_reminder",
                    "complaint_id, recipient_user_id",
                    true,
                    "deleted = 0"));

    @Test
    void schemaEntrypoints_shouldContainIdenticalActiveActionDdlWithCompleteIndexesAndOrdering()
            throws IOException {
        String migration = Files.readString(MIGRATION);
        List<String> migrationStatements = actionStatements(migration);

        assertThat(schemaContractAccepts(migration)).isTrue();
        assertThat(migrationStatements).hasSize(ACTION_TABLES.size() + ACTION_INDEXES.size());
        assertThat(createdTableNames(migrationStatements)).containsExactlyInAnyOrderElementsOf(ACTION_TABLES);
        assertThat(createdIndexNames(migrationStatements)).containsExactlyInAnyOrderElementsOf(ACTION_INDEXES);

        for (Path schemaFile : List.of(INIT_DB, MIGRATE_ALL)) {
            String schemaSql = Files.readString(schemaFile);
            assertThat(schemaContractAccepts(schemaSql))
                    .as(schemaFile + " should satisfy field, foreign-key and unique-index semantics")
                    .isTrue();
            assertThat(actionStatements(schemaSql))
                    .as(schemaFile + " should contain exactly the active migration DDL")
                    .containsExactlyElementsOf(migrationStatements);
        }

        assertFragmentsOrdered(
                INIT_DB,
                "alter table sample_request add column if not exists talent_fans_count",
                "create table if not exists sample_private_note",
                "create index if not exists idx_talent_complaint_reminder_recipient_unread",
                "create table if not exists sample_status_log");
        assertFragmentsOrdered(
                MIGRATE_ALL,
                "create index if not exists idx_order_sync_dedup_claim_row_id",
                "create table if not exists sample_private_note",
                "create index if not exists idx_talent_complaint_reminder_recipient_unread",
                "\\i alter-colonel-activity-recruiter-assignment.sql");
    }

    @Test
    void sqlParser_shouldExcludeCommentedOutDdlFromTheActiveContract() {
        String sql = """
                -- CREATE TABLE IF NOT EXISTS talent_complaint (id UUID);
                /* CREATE UNIQUE INDEX IF NOT EXISTS uk_sample_private_note_owner
                   ON sample_private_note(sample_request_id, user_id); */
                CREATE TABLE IF NOT EXISTS sample_private_note (id UUID);
                """;

        assertThat(actionStatements(sql))
                .containsExactly("create table if not exists sample_private_note (id uuid)");
    }

    @Test
    void schemaContract_shouldRejectSemanticDdlMutations() throws IOException {
        String migration = Files.readString(MIGRATION);
        Map<String, String> mutations = new LinkedHashMap<>();
        mutations.put(
                "private note unique index downgraded",
                migration.replace(
                        "CREATE UNIQUE INDEX IF NOT EXISTS uk_sample_private_note_owner",
                        "CREATE INDEX IF NOT EXISTS uk_sample_private_note_owner"));
        mutations.put(
                "attachment storage key unique index downgraded",
                migration.replace(
                        "CREATE UNIQUE INDEX IF NOT EXISTS uk_talent_complaint_attachment_storage_key",
                        "CREATE INDEX IF NOT EXISTS uk_talent_complaint_attachment_storage_key"));
        mutations.put(
                "reminder unique index downgraded",
                migration.replace(
                        "CREATE UNIQUE INDEX IF NOT EXISTS uk_talent_complaint_reminder_recipient",
                        "CREATE INDEX IF NOT EXISTS uk_talent_complaint_reminder_recipient"));
        mutations.put(
                "private note active-row predicate removed",
                migration.replace(
                        "ON sample_private_note(sample_request_id, user_id)\n    WHERE deleted = 0;",
                        "ON sample_private_note(sample_request_id, user_id);"));
        mutations.put(
                "reminder active-row predicate removed",
                migration.replace(
                        "ON talent_complaint_reminder(complaint_id, recipient_user_id)\n    WHERE deleted = 0;",
                        "ON talent_complaint_reminder(complaint_id, recipient_user_id);"));
        mutations.put(
                "attachment storage key made partial instead of globally unique",
                migration.replace(
                        "ON talent_complaint_attachment(storage_key);",
                        "ON talent_complaint_attachment(storage_key) WHERE deleted = 0;"));
        mutations.put(
                "foreign key target changed",
                migration.replace(
                        "FOREIGN KEY (sample_request_id) REFERENCES sample_request(id)",
                        "FOREIGN KEY (sample_request_id) REFERENCES talent(id)"));
        mutations.put(
                "foreign key local column changed",
                migration.replace(
                        "FOREIGN KEY (user_id) REFERENCES sys_user(id)",
                        "FOREIGN KEY (content) REFERENCES sys_user(id)"));
        mutations.put(
                "required field type changed",
                migration.replace(
                        "content           VARCHAR(200) NOT NULL",
                        "content           TEXT NOT NULL"));

        Map<String, Boolean> mutationResults = new LinkedHashMap<>();
        mutations.forEach((description, sql) ->
                mutationResults.put(description, schemaContractAccepts(sql)));

        assertThat(mutationResults)
                .allSatisfy((description, accepted) ->
                        assertThat(accepted).as(description).isFalse());
    }

    @Test
    void talentComplaintMapper_shouldExposeOnlyAggregateRiskProjection() {
        Method method = requireMethod(
                TalentComplaintMapper.class,
                "selectRiskSummariesByTalentIds",
                Collection.class);
        String sql = selectSql(method);

        assertThat(method.getGenericReturnType().getTypeName())
                .contains("TalentComplaintMapper$TalentRiskSummary");
        assertThat(sql)
                .contains(
                        "count(*) as complaint_count",
                        "max(create_time) as latest_complaint_at",
                        "group by talent_id",
                        "<choose>",
                        "<otherwise>",
                        "and 1 = 0")
                .doesNotContain("select *", "content", "reporter_user_id");

        Class<?> projection = Arrays.stream(TalentComplaintMapper.class.getDeclaredClasses())
                .filter(Class::isRecord)
                .filter(type -> type.getSimpleName().equals("TalentRiskSummary"))
                .findFirst()
                .orElseThrow();
        assertThat(Arrays.stream(projection.getRecordComponents())
                .map(RecordComponent::getName)
                .toList())
                .containsExactly("talentId", "complaintCount", "latestComplaintAt");
    }

    @Test
    void talentComplaintReminderMapper_shouldExposeCursorPageAndUnreadCount() {
        Method pageMethod = requireMethod(
                TalentComplaintReminderMapper.class,
                "selectPageByRecipientUserId",
                UUID.class,
                LocalDateTime.class,
                UUID.class,
                int.class);
        String pageSql = selectSql(pageMethod);

        assertThat(pageSql)
                .contains(
                        "(create_time, id) &lt; (#{beforecreatetime}, #{beforeid})",
                        "order by create_time desc, id desc",
                        "limit #{limit}");
        assertThat(pageSql).doesNotContain("select count(*)");

        Method countMethod = requireMethod(
                TalentComplaintReminderMapper.class,
                "countUnreadByRecipientUserId",
                UUID.class);
        assertThat(countMethod.getReturnType()).isEqualTo(long.class);
        assertThat(selectSql(countMethod))
                .contains(
                        "select count(*)",
                        "recipient_user_id = #{recipientuserid}",
                        "deleted = 0",
                        "read_at is null");
    }

    @Nested
    @DockerAvailable
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class PostgreSql15Integration {

        private PostgreSQLContainer<?> postgres;
        private DataSource dataSource;
        private JdbcTemplate jdbc;

        @BeforeAll
        void startPostgreSql15() {
            postgres = new PostgreSQLContainer<>("postgres:15.17-alpine")
                    .withDatabaseName("cooperation_actions_contract")
                    .withUsername("test")
                    .withPassword("test");
            postgres.start();

            DriverManagerDataSource targetDataSource = new DriverManagerDataSource();
            targetDataSource.setDriverClassName("org.postgresql.Driver");
            targetDataSource.setUrl(postgres.getJdbcUrl());
            targetDataSource.setUsername(postgres.getUsername());
            targetDataSource.setPassword(postgres.getPassword());
            dataSource = targetDataSource;
            jdbc = new JdbcTemplate(dataSource);
        }

        @AfterAll
        void stopPostgreSql15() {
            if (postgres != null) {
                postgres.stop();
            }
        }

        @BeforeEach
        void resetParentSchema() {
            jdbc.execute("DROP SCHEMA public CASCADE");
            jdbc.execute("CREATE SCHEMA public");
            jdbc.execute("CREATE TABLE sys_user (id UUID PRIMARY KEY)");
            jdbc.execute("CREATE TABLE talent (id UUID PRIMARY KEY)");
            jdbc.execute("CREATE TABLE product (id UUID PRIMARY KEY)");
            jdbc.execute("CREATE TABLE sample_request (id UUID PRIMARY KEY)");
        }

        @Test
        void migration_shouldExecuteTwiceAndExposeExpectedPostgreSqlCatalog() {
            executeMigration();
            executeMigration();

            assertThat(jdbc.queryForObject(
                    "SELECT current_setting('server_version')",
                    String.class)).startsWith("15.");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name IN (
                        'sample_private_note',
                        'talent_complaint',
                        'talent_complaint_attachment',
                        'talent_complaint_reminder'
                      )
                    """, Integer.class)).isEqualTo(ACTION_TABLES.size());

            Map<String, ForeignKeyDefinition> foreignKeys = jdbc.query("""
                    SELECT tc.constraint_name,
                           kcu.column_name AS local_column,
                           ccu.table_name AS target_table,
                           ccu.column_name AS target_column
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage kcu
                      ON kcu.constraint_schema = tc.constraint_schema
                     AND kcu.constraint_name = tc.constraint_name
                    JOIN information_schema.constraint_column_usage ccu
                      ON ccu.constraint_schema = tc.constraint_schema
                     AND ccu.constraint_name = tc.constraint_name
                    WHERE tc.constraint_schema = current_schema()
                      AND tc.constraint_type = 'FOREIGN KEY'
                      AND tc.table_name IN (
                        'sample_private_note',
                        'talent_complaint',
                        'talent_complaint_attachment',
                        'talent_complaint_reminder'
                      )
                    """, resultSet -> {
                        Map<String, ForeignKeyDefinition> result = new LinkedHashMap<>();
                        while (resultSet.next()) {
                            result.put(
                                    resultSet.getString("constraint_name"),
                                    new ForeignKeyDefinition(
                                            resultSet.getString("local_column"),
                                            resultSet.getString("target_table"),
                                            resultSet.getString("target_column")));
                        }
                        return result;
                    });
            assertThat(foreignKeys.keySet()).containsExactlyInAnyOrderElementsOf(ACTION_FOREIGN_KEYS);
            assertThat(foreignKeys).containsExactlyInAnyOrderEntriesOf(REQUIRED_FOREIGN_KEYS);

            Map<String, IndexCatalog> indexCatalog = jdbc.query("""
                    SELECT index_class.relname AS index_name,
                           index_meta.indisunique,
                           pg_get_expr(index_meta.indpred, index_meta.indrelid) AS predicate
                    FROM pg_index index_meta
                    JOIN pg_class index_class ON index_class.oid = index_meta.indexrelid
                    JOIN pg_class table_class ON table_class.oid = index_meta.indrelid
                    JOIN pg_namespace namespace ON namespace.oid = table_class.relnamespace
                    WHERE namespace.nspname = current_schema()
                      AND table_class.relname IN (
                        'sample_private_note',
                        'talent_complaint',
                        'talent_complaint_attachment',
                        'talent_complaint_reminder'
                      )
                      AND NOT index_meta.indisprimary
                    """, resultSet -> {
                        Map<String, IndexCatalog> result = new LinkedHashMap<>();
                        while (resultSet.next()) {
                            result.put(
                                    resultSet.getString("index_name"),
                                    new IndexCatalog(
                                            resultSet.getBoolean("indisunique"),
                                            resultSet.getString("predicate")));
                        }
                        return result;
                    });
            assertThat(indexCatalog.keySet()).containsExactlyInAnyOrderElementsOf(ACTION_INDEXES);
            assertThat(indexCatalog.get("uk_sample_private_note_owner").unique()).isTrue();
            assertThat(normalizeCatalogPredicate(
                    indexCatalog.get("uk_sample_private_note_owner").predicate()))
                    .isEqualTo("deleted = 0");
            assertThat(indexCatalog.get("uk_talent_complaint_attachment_storage_key").unique()).isTrue();
            assertThat(normalizeCatalogPredicate(
                    indexCatalog.get("uk_talent_complaint_attachment_storage_key").predicate()))
                    .isEmpty();
            assertThat(indexCatalog.get("uk_talent_complaint_reminder_recipient").unique()).isTrue();
            assertThat(normalizeCatalogPredicate(
                    indexCatalog.get("uk_talent_complaint_reminder_recipient").predicate()))
                    .isEqualTo("deleted = 0");

            Map<String, String> indexDefinitions = jdbc.query("""
                    SELECT indexname, indexdef
                    FROM pg_indexes
                    WHERE schemaname = current_schema()
                      AND tablename IN (
                        'sample_private_note',
                        'talent_complaint',
                        'talent_complaint_attachment',
                        'talent_complaint_reminder'
                      )
                      AND indexname NOT LIKE '%_pkey'
                    """, resultSet -> {
                        Map<String, String> result = new LinkedHashMap<>();
                        while (resultSet.next()) {
                            result.put(resultSet.getString("indexname"), resultSet.getString("indexdef"));
                        }
                        return result;
                    });
            assertThat(indexDefinitions.keySet()).containsExactlyInAnyOrderElementsOf(ACTION_INDEXES);
            assertThat(normalize(indexDefinitions.get("idx_talent_complaint_reminder_recipient_order")))
                    .contains("(recipient_user_id, create_time desc, id desc)", "where (deleted = 0)");
            assertThat(normalize(indexDefinitions.get("idx_talent_complaint_reminder_recipient_unread")))
                    .contains("(recipient_user_id)", "read_at is null");
        }

        @Test
        void mapperQueries_shouldAggregateRisksAndBoundReminderHistory() throws Exception {
            executeMigration();
            SeedData seed = seedCooperationActions();
            SqlSessionFactory sqlSessionFactory = actionMapperSessionFactory();

            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                Object complaintMapper = session.getMapper(TalentComplaintMapper.class);
                Method riskMethod = requireMethod(
                        TalentComplaintMapper.class,
                        "selectRiskSummariesByTalentIds",
                        Collection.class);

                assertThat(invokeList(riskMethod, complaintMapper, (Object) null)).isEmpty();
                assertThat(invokeList(riskMethod, complaintMapper, List.of())).isEmpty();

                List<?> risks = invokeList(
                        riskMethod,
                        complaintMapper,
                        List.of(seed.firstTalentId(), seed.secondTalentId()));
                assertThat(risks).hasSize(2);
                Object firstRisk = risks.get(0);
                assertThat(readRecordComponent(firstRisk, "talentId")).isEqualTo(seed.firstTalentId());
                assertThat(readRecordComponent(firstRisk, "complaintCount")).isEqualTo(2L);
                assertThat(readRecordComponent(firstRisk, "latestComplaintAt"))
                        .isEqualTo(seed.latestComplaintAt());

                Object reminderMapper = session.getMapper(TalentComplaintReminderMapper.class);
                Method pageMethod = requireMethod(
                        TalentComplaintReminderMapper.class,
                        "selectPageByRecipientUserId",
                        UUID.class,
                        LocalDateTime.class,
                        UUID.class,
                        int.class);
                List<TalentComplaintReminder> firstPage = castReminders(invokeList(
                        pageMethod,
                        reminderMapper,
                        seed.recipientUserId(),
                        null,
                        null,
                        2));
                assertThat(firstPage)
                        .extracting(TalentComplaintReminder::getId)
                        .containsExactly(seed.reminderId3(), seed.reminderId2());

                List<TalentComplaintReminder> secondPage = castReminders(invokeList(
                        pageMethod,
                        reminderMapper,
                        seed.recipientUserId(),
                        firstPage.get(1).getCreateTime(),
                        firstPage.get(1).getId(),
                        2));
                assertThat(secondPage)
                        .extracting(TalentComplaintReminder::getId)
                        .containsExactly(seed.reminderId1());

                Method countMethod = requireMethod(
                        TalentComplaintReminderMapper.class,
                        "countUnreadByRecipientUserId",
                        UUID.class);
                assertThat(countMethod.invoke(reminderMapper, seed.recipientUserId())).isEqualTo(2L);
            }
        }

        private void executeMigration() {
            new ResourceDatabasePopulator(new ClassPathResource(MIGRATION_RESOURCE)).execute(dataSource);
        }

        private SqlSessionFactory actionMapperSessionFactory() {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setEnvironment(new Environment(
                    "cooperation-actions-test",
                    new JdbcTransactionFactory(),
                    dataSource));
            configuration.addMapper(TalentComplaintMapper.class);
            configuration.addMapper(TalentComplaintReminderMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        private SeedData seedCooperationActions() {
            UUID reporterUserId = uuid(100);
            UUID recipientUserId = uuid(101);
            UUID firstTalentId = uuid(200);
            UUID secondTalentId = uuid(201);
            UUID productId = uuid(300);
            UUID sampleRequestId = uuid(400);
            UUID complaintId1 = uuid(501);
            UUID complaintId2 = uuid(502);
            UUID complaintId3 = uuid(503);
            UUID reminderId1 = uuid(601);
            UUID reminderId2 = uuid(602);
            UUID reminderId3 = uuid(603);
            LocalDateTime latestComplaintAt = LocalDateTime.of(2026, 7, 16, 12, 0);
            LocalDateTime reminderCreatedAt = LocalDateTime.of(2026, 7, 16, 13, 0);

            jdbc.update("INSERT INTO sys_user(id) VALUES (?), (?)", reporterUserId, recipientUserId);
            jdbc.update("INSERT INTO talent(id) VALUES (?), (?)", firstTalentId, secondTalentId);
            jdbc.update("INSERT INTO product(id) VALUES (?)", productId);
            jdbc.update("INSERT INTO sample_request(id) VALUES (?)", sampleRequestId);

            insertComplaint(
                    complaintId1,
                    sampleRequestId,
                    firstTalentId,
                    productId,
                    reporterUserId,
                    latestComplaintAt.minusHours(1));
            insertComplaint(
                    complaintId2,
                    sampleRequestId,
                    firstTalentId,
                    productId,
                    reporterUserId,
                    latestComplaintAt);
            insertComplaint(
                    complaintId3,
                    sampleRequestId,
                    secondTalentId,
                    productId,
                    reporterUserId,
                    latestComplaintAt.minusHours(2));

            insertReminder(reminderId1, complaintId1, recipientUserId, reminderCreatedAt, null);
            insertReminder(
                    reminderId2,
                    complaintId2,
                    recipientUserId,
                    reminderCreatedAt,
                    reminderCreatedAt.plusMinutes(5));
            insertReminder(reminderId3, complaintId3, recipientUserId, reminderCreatedAt, null);

            return new SeedData(
                    firstTalentId,
                    secondTalentId,
                    recipientUserId,
                    latestComplaintAt,
                    reminderId1,
                    reminderId2,
                    reminderId3);
        }

        private void insertComplaint(
                UUID complaintId,
                UUID sampleRequestId,
                UUID talentId,
                UUID productId,
                UUID reporterUserId,
                LocalDateTime createTime) {
            jdbc.update("""
                    INSERT INTO talent_complaint (
                        id, sample_request_id, talent_id, product_id, reporter_user_id,
                        reason_code, content, create_time
                    ) VALUES (?, ?, ?, ?, ?, 'FULFILLMENT', 'private complaint content', ?)
                    """,
                    complaintId,
                    sampleRequestId,
                    talentId,
                    productId,
                    reporterUserId,
                    createTime);
        }

        private void insertReminder(
                UUID reminderId,
                UUID complaintId,
                UUID recipientUserId,
                LocalDateTime createTime,
                LocalDateTime readAt) {
            jdbc.update("""
                    INSERT INTO talent_complaint_reminder (
                        id, complaint_id, recipient_user_id, read_at, create_time
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    reminderId,
                    complaintId,
                    recipientUserId,
                    readAt,
                    createTime);
        }
    }

    private static Method requireMethod(Class<?> mapperType, String name, Class<?>... parameterTypes) {
        Method method = Arrays.stream(mapperType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), parameterTypes))
                .findFirst()
                .orElse(null);
        assertThat(method)
                .as(mapperType.getSimpleName() + "." + name + " should expose the bounded domain query")
                .isNotNull();
        return method;
    }

    private static String selectSql(Method method) {
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as(method + " should use an explicit domain SELECT").isNotNull();
        return normalize(String.join(" ", select.value()));
    }

    private static List<String> actionStatements(String rawSql) {
        return splitStatements(stripSqlComments(rawSql)).stream()
                .map(CooperationWorkbenchActionsSchemaContractTest::normalize)
                .filter(Predicate.not(String::isBlank))
                .filter(CooperationWorkbenchActionsSchemaContractTest::isActionStatement)
                .toList();
    }

    private static boolean schemaContractAccepts(String rawSql) {
        try {
            List<String> statements = actionStatements(rawSql);
            if (statements.size() != ACTION_TABLES.size() + ACTION_INDEXES.size()) {
                return false;
            }

            SchemaDefinition schema = parseSchema(statements);
            if (!schema.tables().keySet().equals(ACTION_TABLES)
                    || !schema.indexes().keySet().equals(ACTION_INDEXES)) {
                return false;
            }

            for (Map.Entry<String, Map<String, String>> requiredTable : REQUIRED_COLUMNS.entrySet()) {
                TableDefinition actualTable = schema.tables().get(requiredTable.getKey());
                if (actualTable == null || !actualTable.columns().equals(requiredTable.getValue())) {
                    return false;
                }
            }

            Map<String, ForeignKeyDefinition> actualForeignKeys = new LinkedHashMap<>();
            for (TableDefinition table : schema.tables().values()) {
                for (Map.Entry<String, ForeignKeyDefinition> foreignKey : table.foreignKeys().entrySet()) {
                    if (actualForeignKeys.put(foreignKey.getKey(), foreignKey.getValue()) != null) {
                        return false;
                    }
                }
            }
            if (!actualForeignKeys.equals(REQUIRED_FOREIGN_KEYS)) {
                return false;
            }

            return REQUIRED_UNIQUE_INDEXES.entrySet().stream()
                    .allMatch(required -> required.getValue().equals(schema.indexes().get(required.getKey())));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static SchemaDefinition parseSchema(List<String> statements) {
        Map<String, TableDefinition> tables = new LinkedHashMap<>();
        Map<String, IndexDefinition> indexes = new LinkedHashMap<>();
        for (String statement : statements) {
            if (statement.startsWith("create table if not exists ")) {
                Map.Entry<String, TableDefinition> table = parseTable(statement);
                if (tables.put(table.getKey(), table.getValue()) != null) {
                    throw new IllegalArgumentException("duplicate table " + table.getKey());
                }
            } else if (statement.startsWith("create index if not exists ")
                    || statement.startsWith("create unique index if not exists ")) {
                Map.Entry<String, IndexDefinition> index = parseIndex(statement);
                if (indexes.put(index.getKey(), index.getValue()) != null) {
                    throw new IllegalArgumentException("duplicate index " + index.getKey());
                }
            } else {
                throw new IllegalArgumentException("unsupported action statement " + statement);
            }
        }
        return new SchemaDefinition(tables, indexes);
    }

    private static Map.Entry<String, TableDefinition> parseTable(String statement) {
        String prefix = "create table if not exists ";
        int openParenthesis = statement.indexOf('(', prefix.length());
        int closeParenthesis = findMatchingParenthesis(statement, openParenthesis);
        if (openParenthesis < 0 || closeParenthesis != statement.length() - 1) {
            throw new IllegalArgumentException("invalid table DDL " + statement);
        }

        String tableName = statement.substring(prefix.length(), openParenthesis).trim();
        Map<String, String> columns = new LinkedHashMap<>();
        Map<String, ForeignKeyDefinition> foreignKeys = new LinkedHashMap<>();
        for (String fragment : splitTopLevel(statement.substring(openParenthesis + 1, closeParenthesis))) {
            String definition = normalize(fragment);
            if (definition.startsWith("constraint ")) {
                Matcher matcher = FOREIGN_KEY_PATTERN.matcher(definition);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException("invalid foreign key DDL " + definition);
                }
                ForeignKeyDefinition foreignKey = new ForeignKeyDefinition(
                        matcher.group(2), matcher.group(3), matcher.group(4));
                if (foreignKeys.put(matcher.group(1), foreignKey) != null) {
                    throw new IllegalArgumentException("duplicate foreign key " + matcher.group(1));
                }
                continue;
            }

            int separator = definition.indexOf(' ');
            if (separator < 1) {
                throw new IllegalArgumentException("invalid column DDL " + definition);
            }
            String columnName = definition.substring(0, separator);
            String columnDefinition = definition.substring(separator + 1).trim();
            if (columns.put(columnName, columnDefinition) != null) {
                throw new IllegalArgumentException("duplicate column " + columnName);
            }
        }
        return Map.entry(tableName, new TableDefinition(columns, foreignKeys));
    }

    private static Map.Entry<String, IndexDefinition> parseIndex(String statement) {
        String uniquePrefix = "create unique index if not exists ";
        String regularPrefix = "create index if not exists ";
        boolean unique = statement.startsWith(uniquePrefix);
        String prefix = unique ? uniquePrefix : regularPrefix;
        int onIndex = statement.indexOf(" on ", prefix.length());
        int openParenthesis = statement.indexOf('(', onIndex + 4);
        int closeParenthesis = findMatchingParenthesis(statement, openParenthesis);
        if (onIndex < 0 || openParenthesis < 0) {
            throw new IllegalArgumentException("invalid index DDL " + statement);
        }

        String indexName = statement.substring(prefix.length(), onIndex).trim();
        String tableName = statement.substring(onIndex + 4, openParenthesis).trim();
        String columns = normalize(statement.substring(openParenthesis + 1, closeParenthesis));
        String suffix = statement.substring(closeParenthesis + 1).trim();
        String predicate;
        if (suffix.isEmpty()) {
            predicate = "";
        } else if (suffix.startsWith("where ")) {
            predicate = normalize(suffix.substring("where ".length()));
        } else {
            throw new IllegalArgumentException("invalid index suffix " + suffix);
        }
        return Map.entry(indexName, new IndexDefinition(tableName, columns, unique, predicate));
    }

    private static List<String> splitTopLevel(String value) {
        List<String> fragments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenthesisDepth = 0;
        boolean inSingleQuote = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            char next = index + 1 < value.length() ? value.charAt(index + 1) : '\0';
            if (character == '\'' && inSingleQuote && next == '\'') {
                current.append(character).append(next);
                index++;
                continue;
            }
            if (character == '\'') {
                inSingleQuote = !inSingleQuote;
            } else if (!inSingleQuote && character == '(') {
                parenthesisDepth++;
            } else if (!inSingleQuote && character == ')') {
                parenthesisDepth--;
            } else if (!inSingleQuote && character == ',' && parenthesisDepth == 0) {
                fragments.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        if (inSingleQuote || parenthesisDepth != 0) {
            throw new IllegalArgumentException("unbalanced DDL fragment " + value);
        }
        fragments.add(current.toString());
        return fragments;
    }

    private static int findMatchingParenthesis(String value, int openParenthesis) {
        if (openParenthesis < 0 || value.charAt(openParenthesis) != '(') {
            throw new IllegalArgumentException("missing opening parenthesis " + value);
        }
        int depth = 0;
        boolean inSingleQuote = false;
        for (int index = openParenthesis; index < value.length(); index++) {
            char character = value.charAt(index);
            char next = index + 1 < value.length() ? value.charAt(index + 1) : '\0';
            if (character == '\'' && inSingleQuote && next == '\'') {
                index++;
                continue;
            }
            if (character == '\'') {
                inSingleQuote = !inSingleQuote;
            } else if (!inSingleQuote && character == '(') {
                depth++;
            } else if (!inSingleQuote && character == ')' && --depth == 0) {
                return index;
            }
        }
        throw new IllegalArgumentException("missing closing parenthesis " + value);
    }

    private static boolean isActionStatement(String statement) {
        return ACTION_TABLES.stream().anyMatch(table ->
                statement.startsWith("create table if not exists " + table + " ")
                        || statement.contains(" on " + table + "(")
                        || statement.contains(" on " + table + " ("));
    }

    private static Set<String> createdTableNames(List<String> statements) {
        Set<String> result = new LinkedHashSet<>();
        for (String statement : statements) {
            if (statement.startsWith("create table if not exists ")) {
                result.add(statement.substring(
                        "create table if not exists ".length(),
                        statement.indexOf(" ", "create table if not exists ".length())));
            }
        }
        return result;
    }

    private static Set<String> createdIndexNames(List<String> statements) {
        Set<String> result = new LinkedHashSet<>();
        for (String statement : statements) {
            String uniquePrefix = "create unique index if not exists ";
            String prefix = statement.startsWith(uniquePrefix)
                    ? uniquePrefix
                    : "create index if not exists ";
            if (statement.startsWith(prefix)) {
                result.add(statement.substring(prefix.length(), statement.indexOf(" ", prefix.length())));
            }
        }
        return result;
    }

    private static void assertFragmentsOrdered(
            Path schemaFile,
            String before,
            String firstAction,
            String lastAction,
            String after) throws IOException {
        String sql = normalize(stripSqlComments(Files.readString(schemaFile)));
        int beforeIndex = sql.indexOf(before);
        int firstActionIndex = sql.indexOf(firstAction);
        int lastActionIndex = sql.indexOf(lastAction);
        int afterIndex = sql.indexOf(after);

        assertThat(List.of(beforeIndex, firstActionIndex, lastActionIndex, afterIndex))
                .as(schemaFile + " should expose every active ordering sentinel")
                .allMatch(index -> index >= 0);
        assertThat(beforeIndex).isLessThan(firstActionIndex);
        assertThat(firstActionIndex).isLessThan(lastActionIndex);
        assertThat(lastActionIndex).isLessThan(afterIndex);
    }

    private static String stripSqlComments(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        boolean inSingleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    result.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    index++;
                    result.append(' ');
                }
                continue;
            }
            if (inSingleQuote) {
                result.append(current);
                if (current == '\'' && next == '\'') {
                    result.append(next);
                    index++;
                } else if (current == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (current == '\'') {
                inSingleQuote = true;
                result.append(current);
            } else if (current == '-' && next == '-') {
                inLineComment = true;
                index++;
            } else if (current == '/' && next == '*') {
                inBlockComment = true;
                index++;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();
        boolean inSingleQuote = false;

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (current == '\'' && inSingleQuote && next == '\'') {
                currentStatement.append(current).append(next);
                index++;
                continue;
            }
            if (current == '\'') {
                inSingleQuote = !inSingleQuote;
                currentStatement.append(current);
            } else if (current == ';' && !inSingleQuote) {
                statements.add(currentStatement.toString());
                currentStatement.setLength(0);
            } else {
                currentStatement.append(current);
            }
        }
        if (!currentStatement.toString().isBlank()) {
            statements.add(currentStatement.toString());
        }
        return statements;
    }

    private static String normalize(String sql) {
        return sql == null ? "" : sql.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static String normalizeCatalogPredicate(String predicate) {
        String normalized = normalize(predicate);
        while (normalized.startsWith("(")
                && normalized.endsWith(")")
                && findMatchingParenthesis(normalized, 0) == normalized.length() - 1) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static List<?> invokeList(Method method, Object mapper, Object... arguments) throws Exception {
        return (List<?>) method.invoke(mapper, arguments);
    }

    @SuppressWarnings("unchecked")
    private static List<TalentComplaintReminder> castReminders(List<?> reminders) {
        return (List<TalentComplaintReminder>) reminders;
    }

    private static Object readRecordComponent(Object record, String componentName) throws Exception {
        return record.getClass().getMethod(componentName).invoke(record);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }

    private record SeedData(
            UUID firstTalentId,
            UUID secondTalentId,
            UUID recipientUserId,
            LocalDateTime latestComplaintAt,
            UUID reminderId1,
            UUID reminderId2,
            UUID reminderId3) {
    }

    private record ForeignKeyDefinition(
            String localColumn,
            String targetTable,
            String targetColumn) {
    }

    private record IndexDefinition(
            String table,
            String columns,
            boolean unique,
            String predicate) {
    }

    private record TableDefinition(
            Map<String, String> columns,
            Map<String, ForeignKeyDefinition> foreignKeys) {
    }

    private record SchemaDefinition(
            Map<String, TableDefinition> tables,
            Map<String, IndexDefinition> indexes) {
    }

    private record IndexCatalog(boolean unique, String predicate) {
    }
}
