package com.colonel.saas.controller;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
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
        environment.setActiveProfiles("real-pre", "real");
        SystemEnvController controller = new SystemEnvController(
                environment,
                " real-pre ",
                true,
                false,
                " saas_real ",
                "jdbc:postgresql://localhost:5432/ignored"
        );

        ApiResult<Map<String, Object>> result = controller.env();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData())
                .containsEntry("activeProfiles", List.of("real-pre", "real"))
                .containsEntry("environmentLabel", "REAL-PRE")
                .containsEntry("appTestEnabled", true)
                .containsEntry("douyinTestEnabled", false)
                .containsEntry("database", "saas_real");
    }

    @Test
    void env_fallsBackToDefaultProfilesAndJdbcUrlDatabaseName() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("local-mock");
        SystemEnvController controller = new SystemEnvController(
                environment,
                "",
                false,
                true,
                "",
                "jdbc:postgresql://localhost:5432/saas_test?currentSchema=public"
        );

        Map<String, Object> body = controller.env().getData();

        assertThat(body)
                .containsEntry("activeProfiles", List.of("local-mock"))
                .containsEntry("environmentLabel", "LOCAL-MOCK")
                .containsEntry("douyinTestEnabled", true)
                .containsEntry("database", "saas_test");
    }

    @Test
    void env_usesLastPathSegmentWhenDatasourceUrlIsMalformed() {
        MockEnvironment environment = new MockEnvironment();
        SystemEnvController controller = new SystemEnvController(
                environment,
                "",
                false,
                false,
                "",
                "postgresql://localhost:5432/db name"
        );

        assertThat(controller.env().getData()).containsEntry("database", "db name");
    }

    @Test
    void env_returnsUnknownWhenNoDatabaseSignalExists() {
        MockEnvironment environment = new MockEnvironment();
        SystemEnvController controller = new SystemEnvController(environment, "", false, false, "", "");

        assertThat(controller.env().getData()).containsEntry("database", "unknown");
    }

    @Test
    void env_requiresAdminRoleWhenProdProfileIsActive() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        SystemEnvController controller = new SystemEnvController(
                environment,
                "prod",
                false,
                false,
                "colonel_saas",
                ""
        );

        assertThatThrownBy(controller::env)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/env");
        request.setAttribute("roleCodes", List.of(RoleCodes.ADMIN));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThat(controller.env().getData()).containsEntry("environmentLabel", "PROD");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void health_returnsOnlyUpStatus() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        SystemEnvController controller = new SystemEnvController(environment, "test", true, true, "saas_test", "");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").doesNotExist())
                .andExpect(jsonPath("$.activeProfiles").doesNotExist());
    }
}
