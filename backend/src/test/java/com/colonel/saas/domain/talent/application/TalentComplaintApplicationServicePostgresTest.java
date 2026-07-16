package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.config.CustomMetaObjectHandler;
import com.colonel.saas.domain.talent.infrastructure.ComplaintAttachmentStorage;
import com.colonel.saas.domain.talent.port.TalentVisibilityLookup;
import com.colonel.saas.domain.talent.policy.ComplaintImagePolicy;
import com.colonel.saas.domain.talent.policy.TalentComplaintPolicy;
import com.colonel.saas.domain.user.port.UserRoleRecipientLookup;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.dto.talent.TalentComplaintCreateRequest;
import com.colonel.saas.mapper.TalentComplaintAttachmentMapper;
import com.colonel.saas.mapper.TalentComplaintMapper;
import com.colonel.saas.mapper.TalentComplaintReminderMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.testsupport.DockerAvailable;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

@DockerAvailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TalentComplaintApplicationServicePostgresTest {

    private static final String MIGRATION =
            "db/migrate/V20260716_001__cooperation_workbench_actions.sql";

    @TempDir
    Path tempDir;

    private PostgreSQLContainer<?> postgres;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private AnnotationConfigApplicationContext context;
    private UUID sampleId;
    private UUID talentId;
    private UUID productId;
    private UUID reporterId;
    private UUID recipientId;

    @BeforeAll
    void startPostgreSql15() {
        postgres = new PostgreSQLContainer<>("postgres:15.17-alpine")
                .withDatabaseName("talent_complaint_transaction")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        DriverManagerDataSource target = new DriverManagerDataSource();
        target.setDriverClassName("org.postgresql.Driver");
        target.setUrl(postgres.getJdbcUrl());
        target.setUsername(postgres.getUsername());
        target.setPassword(postgres.getPassword());
        dataSource = target;
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    void stopPostgreSql15() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void resetSchemaAndParents() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        jdbc.execute("CREATE TABLE sys_user (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE talent (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE product (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE sample_request (id UUID PRIMARY KEY, status VARCHAR(32) NOT NULL)");
        // 使用 Task6 的生产 migration；父表仅保留其外键所需列与状态观察列。
        new ResourceDatabasePopulator(new ClassPathResource(MIGRATION)).execute(dataSource);

        sampleId = UUID.randomUUID();
        talentId = UUID.randomUUID();
        productId = UUID.randomUUID();
        reporterId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        jdbc.update("INSERT INTO sample_request(id, status) VALUES (?, 'SHIPPING')", sampleId);
        jdbc.update("INSERT INTO talent(id) VALUES (?)", talentId);
        jdbc.update("INSERT INTO product(id) VALUES (?)", productId);
        jdbc.update("INSERT INTO sys_user(id) VALUES (?), (?)", reporterId, recipientId);
    }

    @AfterEach
    void closeContextAndCleanTempRoot() throws IOException {
        if (context != null) {
            context.close();
            context = null;
        }
        deleteChildren(tempDir);
        try (var remaining = Files.list(tempDir)) {
            assertThat(remaining.toList()).isEmpty();
        }
    }

    @Test
    void create_shouldCommitEmptyContentAttachmentReminderAndKeepSampleStatus() throws Exception {
        RecordingComplaintAttachmentStorage storage =
                new RecordingComplaintAttachmentStorage(tempDir, jdbc, sampleId);
        TalentComplaintApplicationService service = createTransactionalService(
                storage, List.of(recipientId));

        var result = service.create(
                sampleId,
                talentId,
                productId,
                reporterId,
                new TalentComplaintCreateRequest("LOW_PRICE_RESALE", null),
                List.of(jpeg()));

        assertThat(result.content()).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT content FROM talent_complaint WHERE id = ?",
                String.class,
                result.id())).isEmpty();
        assertTableCounts(1, 1, 1);
        assertThat(storage.fileExistedAfterStore()).isTrue();
        assertThat(storage.complaintExistedBeforeStore()).isTrue();
        assertThat(storage.lastStorageKey()).isNotBlank();
        assertThat(Files.isRegularFile(tempDir.resolve(storage.lastStorageKey()))).isTrue();
        assertSampleStatusUnchanged();

        storage.deleteQuietly(storage.lastStorageKey());
        assertThat(countRegularFiles(tempDir)).isZero();
    }

    @Test
    void create_shouldRollbackDatabaseAndDeleteFileWhenReminderFailsAfterAttachmentInsert() {
        // 测试故障只存在于测试 schema：触发器先证明同事务附件元数据存在，再强制提醒写入失败。
        jdbc.execute("""
                CREATE FUNCTION fail_reminder_after_attachment() RETURNS trigger AS $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM talent_complaint_attachment
                        WHERE complaint_id = NEW.complaint_id
                    ) THEN
                        RAISE EXCEPTION 'attachment metadata missing before reminder';
                    END IF;
                    RAISE EXCEPTION 'forced reminder failure after attachment';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER trg_fail_reminder_after_attachment
                BEFORE INSERT ON talent_complaint_reminder
                FOR EACH ROW EXECUTE FUNCTION fail_reminder_after_attachment()
                """);
        RecordingComplaintAttachmentStorage storage =
                new RecordingComplaintAttachmentStorage(tempDir, jdbc, sampleId);
        TalentComplaintApplicationService service = createTransactionalService(
                storage, List.of(recipientId));

        Throwable thrown = catchThrowable(() -> service.create(
                sampleId,
                talentId,
                productId,
                reporterId,
                new TalentComplaintCreateRequest("REPEATED_NO_FULFILLMENT", "证据"),
                List.of(jpeg())));

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(rootCause(thrown).getMessage())
                .contains("forced reminder failure after attachment");
        assertThat(storage.fileExistedAfterStore()).isTrue();
        assertThat(storage.complaintExistedBeforeStore()).isTrue();
        assertThat(storage.lastStorageKey()).isNotBlank();
        assertTableCounts(0, 0, 0);
        assertThat(countRegularFiles(tempDir)).isZero();
        assertSampleStatusUnchanged();
    }

    @Test
    void create_shouldDeleteFirstStoredFileWhenSecondAttachmentStorageFails() {
        FailSecondComplaintAttachmentStorage storage =
                new FailSecondComplaintAttachmentStorage(tempDir);
        TalentComplaintApplicationService service =
                createTransactionalService(storage, List.of(recipientId));

        Throwable thrown = catchThrowable(() -> service.create(
                sampleId,
                talentId,
                productId,
                reporterId,
                new TalentComplaintCreateRequest("LOW_PRICE_RESALE", ""),
                List.of(jpeg(), new MockMultipartFile(
                        "files",
                        "proof-two.jpg",
                        "image/jpeg",
                        new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x02}))));

        assertThat(rootCause(thrown))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("second attachment failed");
        assertTableCounts(0, 0, 0);
        assertSampleStatusUnchanged();
        assertThat(countRegularFiles(tempDir)).isZero();
    }

    @Test
    void create_shouldRollbackComplaintWhenRealStorageRootIsAFile() throws Exception {
        Path conflictingRoot = tempDir.resolve("not-a-directory");
        Files.writeString(conflictingRoot, "controlled storage conflict");
        RecordingComplaintAttachmentStorage storage =
                new RecordingComplaintAttachmentStorage(conflictingRoot, jdbc, sampleId);
        TalentComplaintApplicationService service = createTransactionalService(
                storage, List.of(recipientId));

        Throwable thrown = catchThrowable(() -> service.create(
                sampleId,
                talentId,
                productId,
                reporterId,
                new TalentComplaintCreateRequest("LOW_PRICE_RESALE", "证据"),
                List.of(jpeg())));

        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("投诉附件写入失败");
        assertThat(storage.fileExistedAfterStore()).isFalse();
        assertThat(storage.complaintExistedBeforeStore()).isTrue();
        assertTableCounts(0, 0, 0);
        assertSampleStatusUnchanged();

        Files.delete(conflictingRoot);
        assertThat(countRegularFiles(tempDir)).isZero();
    }

    private TalentComplaintApplicationService createTransactionalService(
            ComplaintAttachmentStorage storage,
            List<UUID> recipients) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        globalConfig.setMetaObjectHandler(new CustomMetaObjectHandler());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment(
                "talent-complaint-spring-transaction",
                new SpringManagedTransactionFactory(),
                dataSource));
        configuration.addMapper(TalentComplaintMapper.class);
        configuration.addMapper(TalentComplaintAttachmentMapper.class);
        configuration.addMapper(TalentComplaintReminderMapper.class);
        SqlSessionFactory sqlSessionFactory =
                new MybatisSqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);

        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(SqlSessionFactory.class, () -> sqlSessionFactory);
        context.registerBean(SqlSessionTemplate.class, () -> sqlSessionTemplate);
        context.registerBean(TalentComplaintMapper.class,
                () -> sqlSessionTemplate.getMapper(TalentComplaintMapper.class));
        context.registerBean(TalentComplaintAttachmentMapper.class,
                () -> sqlSessionTemplate.getMapper(TalentComplaintAttachmentMapper.class));
        context.registerBean(TalentComplaintReminderMapper.class,
                () -> sqlSessionTemplate.getMapper(TalentComplaintReminderMapper.class));
        context.registerBean(UserRoleRecipientLookup.class, () -> roleCodes -> recipients);
        context.registerBean(TalentVisibilityLookup.class,
                () -> (requestedTalentIds, userId, deptId, dataScope, unrestricted) ->
                        List.copyOf(requestedTalentIds));
        context.registerBean(TalentComplaintPolicy.class, TalentComplaintPolicy::new);
        context.registerBean(ComplaintImagePolicy.class, ComplaintImagePolicy::new);
        context.registerBean(ComplaintAttachmentStorage.class, () -> storage);
        context.registerBean(CurrentUserPermissionChecker.class,
                () -> new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()));
        context.registerBean(OperationLogService.class, () -> mock(OperationLogService.class));
        context.registerBean(TalentComplaintApplicationService.class);
        context.refresh();

        TalentComplaintApplicationService service =
                context.getBean(TalentComplaintApplicationService.class);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        return service;
    }

    private void assertTableCounts(int complaints, int attachments, int reminders) {
        assertThat(count("talent_complaint")).isEqualTo(complaints);
        assertThat(count("talent_complaint_attachment")).isEqualTo(attachments);
        assertThat(count("talent_complaint_reminder")).isEqualTo(reminders);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void assertSampleStatusUnchanged() {
        assertThat(jdbc.queryForObject(
                "SELECT status FROM sample_request WHERE id = ?", String.class, sampleId))
                .isEqualTo("SHIPPING");
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile(
                "files",
                "proof.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01});
    }

    private long countRegularFiles(Path root) {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void deleteChildren(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            paths.filter(path -> !path.equals(root))
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }

    private static final class RecordingComplaintAttachmentStorage
            extends ComplaintAttachmentStorage {

        private final Path root;
        private final JdbcTemplate jdbc;
        private final UUID sampleId;
        private String lastStorageKey;
        private boolean fileExistedAfterStore;
        private boolean complaintExistedBeforeStore;

        private RecordingComplaintAttachmentStorage(
                Path root,
                JdbcTemplate jdbc,
                UUID sampleId) {
            super(root);
            this.root = root;
            this.jdbc = jdbc;
            this.sampleId = sampleId;
        }

        @Override
        public StoredAttachment store(ComplaintImagePolicy.ValidatedImage image) {
            complaintExistedBeforeStore = jdbc.queryForObject("""
                    SELECT COUNT(*) = 1
                    FROM talent_complaint
                    WHERE sample_request_id = ?
                    """, Boolean.class, sampleId);
            StoredAttachment stored = super.store(image);
            lastStorageKey = stored.storageKey();
            fileExistedAfterStore = Files.isRegularFile(root.resolve(lastStorageKey));
            return stored;
        }

        private String lastStorageKey() {
            return lastStorageKey;
        }

        private boolean fileExistedAfterStore() {
            return fileExistedAfterStore;
        }

        private boolean complaintExistedBeforeStore() {
            return complaintExistedBeforeStore;
        }
    }

    private static final class FailSecondComplaintAttachmentStorage
            extends ComplaintAttachmentStorage {

        private int stores;

        private FailSecondComplaintAttachmentStorage(Path root) {
            super(root);
        }

        @Override
        public StoredAttachment store(ComplaintImagePolicy.ValidatedImage image) {
            stores++;
            if (stores == 2) {
                throw new IllegalStateException(
                        "投诉附件写入失败", new IOException("second attachment failed"));
            }
            return super.store(image);
        }
    }
}
