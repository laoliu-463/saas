package com.colonel.saas.controller;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SystemEnvControllerTest {

    @Test
    void env_prefersExplicitEnvLabelAndDatabaseNameProperty() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");
        SystemEnvController controller = new SystemEnvController(
                environment,
                new CurrentUserPermissionPolicy(),
                " real-pre ",
                true,
                false,
                " saas_real ",
                "jdbc:postgresql://localhost:5432/ignored",
                "test-sha",
                "sha256:test"
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/env");
        request.setAttribute("roleCodes", List.of(RoleCodes.ADMIN));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ApiResult<Map<String, Object>> result;
        try {
            result = controller.env();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData())
                .containsEntry("activeProfiles", List.of("real-pre"))
                .containsEntry("environmentLabel", "REAL-PRE")
                .containsEntry("appTestEnabled", true)
                .containsEntry("douyinTestEnabled", false)
                .containsEntry("database", "saas_real");
    }

    @Test
    void env_fallsBackToDefaultProfilesAndJdbcUrlDatabaseName() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("test");
        SystemEnvController controller = new SystemEnvController(
                environment,
                new CurrentUserPermissionPolicy(),
                "",
                false,
                true,
                "",
                "jdbc:postgresql://localhost:5432/saas_test?currentSchema=public",
                "test-sha",
                "sha256:test"
        );

        Map<String, Object> body = controller.env().getData();

        assertThat(body)
                .containsEntry("activeProfiles", List.of("test"))
                .containsEntry("environmentLabel", "TEST")
                .containsEntry("douyinTestEnabled", true)
                .containsEntry("database", "saas_test");
    }

    @Test
    void env_usesLastPathSegmentWhenDatasourceUrlIsMalformed() {
        MockEnvironment environment = new MockEnvironment();
        SystemEnvController controller = new SystemEnvController(
                environment,
                new CurrentUserPermissionPolicy(),
                "",
                false,
                false,
                "",
                "postgresql://localhost:5432/db name",
                "test-sha",
                "sha256:test"
        );

        assertThat(controller.env().getData()).containsEntry("database", "db name");
    }

    @Test
    void env_returnsUnknownWhenNoDatabaseSignalExists() {
        MockEnvironment environment = new MockEnvironment();
        SystemEnvController controller = new SystemEnvController(
                environment,
                new CurrentUserPermissionPolicy(),
                "",
                false,
                false,
                "",
                "",
                "test-sha",
                "sha256:test"
        );

        assertThat(controller.env().getData()).containsEntry("database", "unknown");
    }

    @Test
    void env_requiresAdminRoleWhenProtectedProfileIsActive() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");
        SystemEnvController controller = new SystemEnvController(
                environment,
                new CurrentUserPermissionPolicy(),
                "real-pre",
                false,
                false,
                "colonel_saas",
                "",
                "test-sha",
                "sha256:test"
        );

        assertThatThrownBy(controller::env)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/env");
        request.setAttribute("roleCodes", List.of(RoleCodes.ADMIN));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThat(controller.env().getData()).containsEntry("environmentLabel", "REAL-PRE");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void env_allowsNormalizedAdminRoleStringWhenProtectedProfileIsActive() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");
        SystemEnvController controller = new SystemEnvController(
                environment,
                new CurrentUserPermissionPolicy(),
                "real-pre",
                false,
                false,
                "colonel_saas",
                "",
                "test-sha",
                "sha256:test"
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/env");
        request.setAttribute("roleCodes", "[ ADMIN ]");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThat(controller.env().getData()).containsEntry("environmentLabel", "REAL-PRE");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void health_returnsUpStatusAndImmutableReleaseIdentity() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        SystemEnvController controller = new SystemEnvController(
                environment,
                new CurrentUserPermissionPolicy(),
                "test",
                true,
                true,
                "saas_test",
                "",
                "0123456789abcdef0123456789abcdef01234567",
                "sha256:0123456789abcdef"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.gitSha").value("0123456789abcdef0123456789abcdef01234567"))
                .andExpect(jsonPath("$.imageDigest").value("sha256:0123456789abcdef"))
                .andExpect(jsonPath("$.database").doesNotExist())
                .andExpect(jsonPath("$.activeProfiles").doesNotExist());
    }
}
