package com.colonel.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class StartupEnvironmentLoggerTest {

    @Test
    void run_logsResolvedEnvironmentWithoutThrowing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");
        StartupEnvironmentLogger logger = new StartupEnvironmentLogger(
                environment,
                false,
                false,
                " saas_real ",
                "jdbc:postgresql://localhost:5432/ignored",
                " real-pre "
        );

        logger.run(null);

        assertThat((String) ReflectionTestUtils.invokeMethod(logger, "activeProfileLabel"))
                .isEqualTo("real-pre");
        assertThat((String) ReflectionTestUtils.invokeMethod(logger, "resolveDatabaseName"))
                .isEqualTo("saas_real");
    }

    @Test
    void resolveDatabaseName_readsJdbcUrlPathAndMalformedUrlFallback() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("default-test");
        StartupEnvironmentLogger fromJdbcUrl = new StartupEnvironmentLogger(
                environment,
                false,
                true,
                "",
                "jdbc:postgresql://localhost:5432/saas_test?currentSchema=public",
                ""
        );
        StartupEnvironmentLogger malformedUrl = new StartupEnvironmentLogger(
                environment,
                false,
                true,
                "",
                "postgresql://localhost:5432/db name",
                ""
        );
        StartupEnvironmentLogger unknown = new StartupEnvironmentLogger(environment, false, true, "", "", "");

        assertThat((String) ReflectionTestUtils.invokeMethod(fromJdbcUrl, "activeProfileLabel"))
                .isEqualTo("default-test");
        assertThat((String) ReflectionTestUtils.invokeMethod(fromJdbcUrl, "resolveDatabaseName"))
                .isEqualTo("saas_test");
        assertThat((String) ReflectionTestUtils.invokeMethod(malformedUrl, "resolveDatabaseName"))
                .isEqualTo("db name");
        assertThat((String) ReflectionTestUtils.invokeMethod(unknown, "resolveDatabaseName"))
                .isEqualTo("unknown");
    }
}
