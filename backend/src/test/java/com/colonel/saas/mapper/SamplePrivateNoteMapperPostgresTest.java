package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.entity.SamplePrivateNote;
import com.colonel.saas.testsupport.DockerAvailable;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DockerAvailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SamplePrivateNoteMapperPostgresTest {

    private static final String MIGRATION =
            "db/migrate/V20260716_001__cooperation_workbench_actions.sql";

    private PostgreSQLContainer<?> postgres;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    void startPostgreSql15() {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("sample_private_note_mapper")
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

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment(
                "sample-private-note-postgres",
                new JdbcTransactionFactory(),
                dataSource));
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        configuration.addMapper(SamplePrivateNoteMapper.class);
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    void stopPostgreSql15() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void resetSchema() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        jdbc.execute("CREATE TABLE sys_user (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE talent (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE product (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE sample_request (id UUID PRIMARY KEY)");
        new ResourceDatabasePopulator(new ClassPathResource(MIGRATION)).execute(dataSource);
    }

    @Test
    void upsert_shouldInsertThenUpdateSingleActiveRowWithAuditFields() throws Exception {
        assertThat(jdbc.queryForObject(
                "SELECT current_setting('server_version')", String.class)).startsWith("15.");
        Method upsert = requireMapperMethod("upsertActive", SamplePrivateNote.class);
        UUID sampleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        insertParents(sampleId, userId);

        SamplePrivateNote first = note(sampleId, userId, "首次备注");
        SamplePrivateNote second = note(sampleId, userId, "更新备注");
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            SamplePrivateNoteMapper mapper = session.getMapper(SamplePrivateNoteMapper.class);
            assertThat(invokeInt(upsert, mapper, first)).isEqualTo(1);
            assertThat(invokeInt(upsert, mapper, second)).isEqualTo(1);

            SamplePrivateNote active = mapper.selectBySampleRequestAndUser(sampleId, userId);
            assertThat(active.getId()).isEqualTo(first.getId());
            assertThat(active.getContent()).isEqualTo("更新备注");
            assertThat(active.getVersion()).isEqualTo(1);
            assertThat(active.getCreateBy()).isEqualTo(userId);
            assertThat(active.getUpdateBy()).isEqualTo(userId);
        }

        assertThat(activeCount(sampleId, userId)).isEqualTo(1);
    }

    @Test
    void concurrentFirstSave_shouldNotFailOrCreateDuplicateActiveRows() throws Exception {
        Method upsert = requireMapperMethod("upsertActive", SamplePrivateNote.class);
        UUID sampleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        insertParents(sampleId, userId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> concurrentUpsert(
                    upsert, note(sampleId, userId, "并发备注一"), start));
            Future<Integer> second = executor.submit(() -> concurrentUpsert(
                    upsert, note(sampleId, userId, "并发备注二"), start));
            start.countDown();

            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(activeCount(sampleId, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT content
                FROM sample_private_note
                WHERE sample_request_id = ? AND user_id = ? AND deleted = 0
                """, String.class, sampleId, userId))
                .isIn("并发备注一", "并发备注二");
        assertThat(jdbc.queryForObject("""
                SELECT version
                FROM sample_private_note
                WHERE sample_request_id = ? AND user_id = ? AND deleted = 0
                """, Integer.class, sampleId, userId)).isEqualTo(1);
    }

    @Test
    void softDelete_shouldGuardOwnerAndVersionWithoutAffectingOtherUsersOrRevivingRows() throws Exception {
        Method upsert = requireMapperMethod("upsertActive", SamplePrivateNote.class);
        Method softDelete = requireMapperMethod(
                "softDeleteActive", UUID.class, UUID.class, Integer.class);
        UUID sampleId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        insertParents(sampleId, firstUserId, secondUserId);
        SamplePrivateNote first = note(sampleId, firstUserId, "用户一备注");
        SamplePrivateNote second = note(sampleId, secondUserId, "用户二备注");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            SamplePrivateNoteMapper mapper = session.getMapper(SamplePrivateNoteMapper.class);
            assertThat(invokeInt(upsert, mapper, first)).isEqualTo(1);
            assertThat(invokeInt(upsert, mapper, second)).isEqualTo(1);
            assertThat(invokeInt(softDelete, mapper, first.getId(), firstUserId, 1)).isZero();
            assertThat(invokeInt(softDelete, mapper, first.getId(), secondUserId, 0)).isZero();
            assertThat(invokeInt(softDelete, mapper, first.getId(), firstUserId, 0)).isEqualTo(1);

            assertThat(mapper.selectBySampleRequestAndUser(sampleId, firstUserId)).isNull();
            assertThat(mapper.selectBySampleRequestAndUser(sampleId, secondUserId))
                    .extracting(SamplePrivateNote::getContent)
                    .isEqualTo("用户二备注");

            SamplePrivateNote replacement = note(sampleId, firstUserId, "删除后新备注");
            assertThat(invokeInt(upsert, mapper, replacement)).isEqualTo(1);
            assertThat(mapper.selectBySampleRequestAndUser(sampleId, firstUserId).getId())
                    .isEqualTo(replacement.getId());
        }

        assertThat(jdbc.queryForObject(
                "SELECT deleted FROM sample_private_note WHERE id = ?",
                Integer.class,
                first.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM sample_private_note WHERE id = ?",
                Integer.class,
                first.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT update_by FROM sample_private_note WHERE id = ?",
                UUID.class,
                first.getId())).isEqualTo(firstUserId);
        assertThat(activeCount(sampleId, firstUserId)).isEqualTo(1);
        assertThat(activeCount(sampleId, secondUserId)).isEqualTo(1);
    }

    private Method requireMapperMethod(String name, Class<?>... parameterTypes) throws Exception {
        assertThat(Arrays.stream(SamplePrivateNoteMapper.class.getMethods())
                .map(Method::getName))
                .contains(name);
        return SamplePrivateNoteMapper.class.getMethod(name, parameterTypes);
    }

    private int concurrentUpsert(
            Method method,
            SamplePrivateNote note,
            CountDownLatch start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return invokeInt(method, session.getMapper(SamplePrivateNoteMapper.class), note);
        }
    }

    private int invokeInt(Method method, Object mapper, Object... arguments) throws Exception {
        try {
            return (Integer) method.invoke(mapper, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }

    private SamplePrivateNote note(UUID sampleId, UUID userId, String content) {
        SamplePrivateNote note = new SamplePrivateNote();
        note.setId(UUID.randomUUID());
        note.setSampleRequestId(sampleId);
        note.setUserId(userId);
        note.setContent(content);
        note.setVersion(0);
        note.setCreateBy(userId);
        note.setUpdateBy(userId);
        return note;
    }

    private void insertParents(UUID sampleId, UUID... userIds) {
        jdbc.update("INSERT INTO sample_request(id) VALUES (?)", sampleId);
        for (UUID userId : Set.of(userIds)) {
            jdbc.update("INSERT INTO sys_user(id) VALUES (?)", userId);
        }
    }

    private int activeCount(UUID sampleId, UUID userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sample_private_note
                WHERE sample_request_id = ? AND user_id = ? AND deleted = 0
                """, Integer.class, sampleId, userId);
    }
}
