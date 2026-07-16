package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.colonel.saas.common.handler.UUIDTypeHandler;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DockerAvailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TalentComplaintReminderMapperPostgresTest {

    private static final String MIGRATION =
            "db/migrate/V20260716_001__cooperation_workbench_actions.sql";

    private PostgreSQLContainer<?> postgres;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    void startPostgreSql15() {
        postgres = new PostgreSQLContainer<>("postgres:15.17-alpine")
                .withDatabaseName("complaint_reminder_mapper")
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
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        configuration.setEnvironment(new Environment(
                "complaint-reminder-postgres",
                new JdbcTransactionFactory(),
                dataSource));
        configuration.addMapper(TalentComplaintReminderMapper.class);
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
    void markRead_shouldAllowExactlyOneConcurrentRecipientUpdate() throws Exception {
        UUID recipientId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID sampleId = UUID.randomUUID();
        UUID complaintId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        insertParents(recipientId, reporterId, talentId, productId, sampleId);
        jdbc.update("""
                INSERT INTO talent_complaint(
                    id, sample_request_id, talent_id, product_id, reporter_user_id,
                    reason_code, content, status, create_by, update_by)
                VALUES (?, ?, ?, ?, ?, 'OTHER', 'evidence', 'SUBMITTED', ?, ?)
                """, complaintId, sampleId, talentId, productId, reporterId, reporterId, reporterId);
        jdbc.update("""
                INSERT INTO talent_complaint_reminder(
                    id, complaint_id, recipient_user_id, create_by, update_by)
                VALUES (?, ?, ?, ?, ?)
                """, reminderId, complaintId, recipientId, reporterId, reporterId);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(
                    () -> concurrentMarkRead(reminderId, recipientId, start));
            Future<Integer> second = executor.submit(
                    () -> concurrentMarkRead(reminderId, recipientId, start));
            start.countDown();

            assertThat(List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT read_at IS NOT NULL FROM talent_complaint_reminder WHERE id = ?",
                Boolean.class,
                reminderId)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT update_by FROM talent_complaint_reminder WHERE id = ?",
                UUID.class,
                reminderId)).isEqualTo(recipientId);
    }

    private int concurrentMarkRead(
            UUID reminderId,
            UUID recipientId,
            CountDownLatch start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.getMapper(TalentComplaintReminderMapper.class)
                    .markRead(reminderId, recipientId, LocalDateTime.now());
        }
    }

    private void insertParents(
            UUID recipientId,
            UUID reporterId,
            UUID talentId,
            UUID productId,
            UUID sampleId) {
        jdbc.update("INSERT INTO sys_user(id) VALUES (?), (?)", recipientId, reporterId);
        jdbc.update("INSERT INTO talent(id) VALUES (?)", talentId);
        jdbc.update("INSERT INTO product(id) VALUES (?)", productId);
        jdbc.update("INSERT INTO sample_request(id) VALUES (?)", sampleId);
    }
}
