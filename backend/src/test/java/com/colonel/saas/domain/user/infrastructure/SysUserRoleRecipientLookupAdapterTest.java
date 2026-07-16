package com.colonel.saas.domain.user.infrastructure;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.mapper.SysUserMapper;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserRoleRecipientLookupAdapterTest {

    private static final Pattern ROLE_CODE_FOREACH_CONTRACT = Pattern.compile(
            "and\\s+sr\\.role_code\\s+in\\s*"
                    + "<foreach\\s+collection=\"rolecodes\"\\s+item=\"rolecode\"\\s+"
                    + "open=\"\\(\"\\s+separator=\",\"\\s+close=\"\\)\">\\s*"
                    + "#\\{rolecode}\\s*</foreach>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Mock
    private SysUserMapper sysUserMapper;

    private SysUserRoleRecipientLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SysUserRoleRecipientLookupAdapter(sysUserMapper);
    }

    @Test
    void findActiveUserIdsByRoleCodes_shouldReturnDistinctStableIds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<String> roleCodes = List.of("admin", "biz_leader", "channel_leader");
        when(sysUserMapper.findActiveIdsByRoleCodes(roleCodes))
                .thenReturn(List.of(first, second, first));

        assertThat(adapter.findActiveUserIdsByRoleCodes(roleCodes))
                .containsExactly(first, second);
    }

    @Test
    void findActiveUserIdsByRoleCodes_emptyRolesShouldNotQueryMapper() {
        assertThat(adapter.findActiveUserIdsByRoleCodes(List.of())).isEmpty();

        verify(sysUserMapper, never()).findActiveIdsByRoleCodes(List.of());
    }

    @Test
    void mapperQuery_shouldFilterActiveUsersRolesAndRelations() throws Exception {
        String sql = mapperSql();

        assertThat(sql)
                .contains("select distinct su.id")
                .contains("from sys_user su")
                .contains("join sys_user_role sur")
                .contains("join sys_role sr")
                .contains("su.deleted = 0")
                .contains("sur.deleted = 0")
                .contains("sr.deleted = 0")
                .contains("sr.status = 1")
                .contains("su.status = 1")
                .contains("<otherwise>")
                .contains("1 = 0");
        assertRoleCodeForeachContract(sql);
    }

    @Test
    void roleCodeForeachContract_shouldRejectBrokenPredicateCollectionOrPlaceholder() throws Exception {
        String sql = mapperSql();

        assertRejectedContractMutation(sql.replace("and sr.role_code in", ""));
        assertRejectedContractMutation(sql.replace("collection=\"rolecodes\"", "collection=\"ids\""));
        assertRejectedContractMutation(sql.replace("#{rolecode}", "#{id}"));
    }

    private static String mapperSql() throws Exception {
        Method method = SysUserMapper.class.getMethod("findActiveIdsByRoleCodes", java.util.Collection.class);
        Select select = method.getAnnotation(Select.class);
        return String.join(" ", select.value()).replaceAll("\\s+", " ").toLowerCase();
    }

    private static void assertRoleCodeForeachContract(String sql) {
        assertThat(sql).containsPattern(ROLE_CODE_FOREACH_CONTRACT);
    }

    private static void assertRejectedContractMutation(String mutatedSql) {
        assertThatThrownBy(() -> assertRoleCodeForeachContract(mutatedSql))
                .isInstanceOf(AssertionError.class);
    }

    @Nested
    @DockerAvailable
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class PostgreSql15Integration {

        private PostgreSQLContainer<?> postgres;
        private JdbcTemplate jdbc;
        private SqlSessionFactory sqlSessionFactory;

        @BeforeAll
        void startPostgreSql15() {
            postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("role_recipient_lookup")
                    .withUsername("test")
                    .withPassword("test");
            postgres.start();

            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(postgres.getJdbcUrl());
            dataSource.setUsername(postgres.getUsername());
            dataSource.setPassword(postgres.getPassword());
            jdbc = new JdbcTemplate(dataSource);

            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setEnvironment(new Environment(
                    "role-recipient-test",
                    new JdbcTransactionFactory(),
                    dataSource));
            configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
            configuration.addMapper(SysUserMapper.class);
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
            jdbc.execute("""
                    CREATE TABLE sys_user (
                        id UUID PRIMARY KEY,
                        status SMALLINT NOT NULL,
                        deleted SMALLINT NOT NULL
                    )
                    """);
            jdbc.execute("""
                    CREATE TABLE sys_role (
                        id UUID PRIMARY KEY,
                        role_code VARCHAR(50) NOT NULL,
                        status SMALLINT NOT NULL,
                        deleted SMALLINT NOT NULL
                    )
                    """);
            jdbc.execute("""
                    CREATE TABLE sys_user_role (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL,
                        role_id UUID NOT NULL,
                        deleted SMALLINT NOT NULL
                    )
                    """);
        }

        @Test
        void adapter_shouldReturnOnlyStableDistinctRecipientsFromActiveUsersAndRoles() {
            UUID activeAdminRole = insertRole(101, RoleCodes.ADMIN, 1, 0);
            UUID activeBizLeaderRole = insertRole(102, RoleCodes.BIZ_LEADER, 1, 0);
            UUID disabledChannelLeaderRole = insertRole(103, RoleCodes.CHANNEL_LEADER, 0, 0);
            UUID deletedChannelLeaderRole = insertRole(104, RoleCodes.CHANNEL_LEADER, 1, 1);
            UUID activeNonTargetRole = insertRole(105, RoleCodes.OPS_STAFF, 1, 0);

            UUID firstActiveRecipient = insertUser(1, 1, 0);
            UUID duplicateRoleRecipient = insertUser(2, 1, 0);
            UUID disabledUser = insertUser(3, 0, 0);
            UUID deletedUser = insertUser(4, 1, 1);
            UUID disabledRoleUser = insertUser(5, 1, 0);
            UUID deletedRoleUser = insertUser(6, 1, 0);
            UUID deletedRelationUser = insertUser(7, 1, 0);
            UUID nonTargetRoleUser = insertUser(8, 1, 0);

            insertRelation(201, firstActiveRecipient, activeAdminRole, 0);
            insertRelation(202, duplicateRoleRecipient, activeAdminRole, 0);
            insertRelation(203, duplicateRoleRecipient, activeBizLeaderRole, 0);
            insertRelation(204, disabledUser, activeAdminRole, 0);
            insertRelation(205, deletedUser, activeAdminRole, 0);
            insertRelation(206, disabledRoleUser, disabledChannelLeaderRole, 0);
            insertRelation(207, deletedRoleUser, deletedChannelLeaderRole, 0);
            insertRelation(208, deletedRelationUser, activeAdminRole, 1);
            insertRelation(209, nonTargetRoleUser, activeNonTargetRole, 0);

            try (SqlSession session = sqlSessionFactory.openSession()) {
                SysUserMapper mapper = session.getMapper(SysUserMapper.class);
                SysUserRoleRecipientLookupAdapter actualAdapter =
                        new SysUserRoleRecipientLookupAdapter(mapper);

                assertThat(actualAdapter.findActiveUserIdsByRoleCodes(List.of(
                        RoleCodes.ADMIN,
                        RoleCodes.BIZ_LEADER,
                        RoleCodes.CHANNEL_LEADER)))
                        .containsExactly(firstActiveRecipient, duplicateRoleRecipient);
                assertThat(actualAdapter.findActiveUserIdsByRoleCodes(List.of())).isEmpty();
                assertThat(mapper.findActiveIdsByRoleCodes(List.of())).isEmpty();
            }
        }

        private UUID insertUser(long suffix, int status, int deleted) {
            UUID id = uuid(suffix);
            jdbc.update(
                    "INSERT INTO sys_user (id, status, deleted) VALUES (?, ?, ?)",
                    id,
                    status,
                    deleted);
            return id;
        }

        private UUID insertRole(long suffix, String roleCode, int status, int deleted) {
            UUID id = uuid(suffix);
            jdbc.update(
                    "INSERT INTO sys_role (id, role_code, status, deleted) VALUES (?, ?, ?, ?)",
                    id,
                    roleCode,
                    status,
                    deleted);
            return id;
        }

        private void insertRelation(long suffix, UUID userId, UUID roleId, int deleted) {
            jdbc.update(
                    "INSERT INTO sys_user_role (id, user_id, role_id, deleted) VALUES (?, ?, ?, ?)",
                    uuid(suffix),
                    userId,
                    roleId,
                    deleted);
        }

        private UUID uuid(long suffix) {
            return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
        }
    }
}
