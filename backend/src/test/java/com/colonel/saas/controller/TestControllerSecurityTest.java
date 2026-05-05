package com.colonel.saas.controller;

import com.colonel.saas.auth.service.AuthService;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.security.JwtAuthInterceptor;
import com.colonel.saas.security.JwtTokenProvider;
import com.colonel.saas.testsupport.TestDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TestControllerSecurityTest {

    @Test
    void optionsOnTestSeed_shouldNotReturn401_inLocalMock() throws Exception {
        TestDataService testDataService = Mockito.mock(TestDataService.class);
        JwtTokenProvider jwtTokenProvider = Mockito.mock(JwtTokenProvider.class);
        AuthService authService = Mockito.mock(AuthService.class);
        JwtAuthInterceptor interceptor = new JwtAuthInterceptor(jwtTokenProvider, authService, new ObjectMapper());

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController(testDataService))
                .addInterceptors(interceptor)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        mockMvc.perform(options("/test/seed"))
                .andExpect(status().isOk());
    }

    @Test
    void testController_shouldNotLoadWhenDisabledOutsideLocalMockOrTest() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.test.enabled=false"
                )
                .withUserConfiguration(TestController.class, TestSupportConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(TestController.class));
    }

    @Test
    void testController_shouldRequireAdminRole() {
        RequireRoles requireRoles = TestController.class.getAnnotation(RequireRoles.class);
        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).containsExactly(RoleCodes.ADMIN);
    }

    @Configuration
    static class TestSupportConfig {
        @Bean
        TestDataService testDataService() {
            return Mockito.mock(TestDataService.class);
        }
    }
}
