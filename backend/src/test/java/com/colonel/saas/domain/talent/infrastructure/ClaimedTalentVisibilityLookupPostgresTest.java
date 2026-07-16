package com.colonel.saas.domain.talent.infrastructure;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.mapper.TalentClaimMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DockerAvailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClaimedTalentVisibilityLookupPostgresTest {

    private PostgreSQLContainer<?> postgres;
    private JdbcTemplate jdbc;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    void startPostgreSql15() {
        postgres = new PostgreSQLContainer<>("postgres:15.17-alpine")
                .withDatabaseName("talent_visibility")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        sqlSessionFactory = mapperSessionFactory(dataSource);
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
        jdbc.execute("""
                CREATE TABLE talent_claim (
                    id UUID PRIMARY KEY,
                    talent_id UUID NOT NULL,
                    talent_uid VARCHAR(50) NOT NULL,
                    user_id UUID NOT NULL,
                    dept_id UUID,
                    claim_type SMALLINT NOT NULL DEFAULT 1,
                    status SMALLINT NOT NULL DEFAULT 1,
                    apply_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
    }

    @Test
    void retainVisibleTalentIds_shouldIntersectActiveClaimsAcrossPersonalAndDepartmentScopes() {
        UUID user = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        UUID otherDept = UUID.randomUUID();
        UUID personal = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        UUID inactive = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        insertClaim(personal, user, dept, 1, 0);
        insertClaim(department, otherUser, dept, 1, 0);
        insertClaim(inactive, user, dept, 0, 0);
        insertClaim(deleted, user, dept, 1, 1);
        insertClaim(outside, otherUser, otherDept, 1, 0);
        List<UUID> requested = List.of(
                department, personal, personal, inactive, deleted, outside);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ClaimedTalentVisibilityLookup lookup = lookup(session);
            assertThat(lookup.retainVisibleTalentIds(
                    requested, user, dept, DataScope.PERSONAL, false))
                    .containsExactly(personal);
            assertThat(lookup.retainVisibleTalentIds(
                    requested, user, dept, DataScope.DEPT, false))
                    .containsExactly(department, personal);
            assertThat(lookup.retainVisibleTalentIds(
                    requested, user, dept, DataScope.ALL, false))
                    .containsExactly(department, personal, inactive, deleted, outside);
            assertThat(lookup.retainVisibleTalentIds(
                    List.of(), user, dept, DataScope.PERSONAL, false)).isEmpty();
        }
    }

    @Test
    void retainVisibleTalentIds_shouldExecutePostgresInQueryAtOneHundredIds() {
        UUID user = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        List<UUID> requested = new ArrayList<>(IntStream.range(0, 100)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList());
        insertClaim(requested.get(0), user, dept, 1, 0);
        insertClaim(requested.get(99), user, dept, 1, 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            assertThat(lookup(session).retainVisibleTalentIds(
                    requested, user, dept, DataScope.PERSONAL, false))
                    .containsExactly(requested.get(0), requested.get(99));
        }
    }

    private ClaimedTalentVisibilityLookup lookup(SqlSession session) {
        return new ClaimedTalentVisibilityLookup(
                session.getMapper(TalentClaimMapper.class),
                new DataScopeResolver(new DataScopePolicy()));
    }

    private SqlSessionFactory mapperSessionFactory(DataSource dataSource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        configuration.setEnvironment(new Environment(
                "talent-visibility-postgres",
                new JdbcTransactionFactory(),
                dataSource));
        configuration.addMapper(TalentClaimMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private void insertClaim(
            UUID talentId,
            UUID userId,
            UUID deptId,
            int status,
            int deleted) {
        jdbc.update("""
                INSERT INTO talent_claim(
                    id, talent_id, talent_uid, user_id, dept_id, status, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), talentId, talentId.toString(), userId, deptId, status, deleted);
    }
}
