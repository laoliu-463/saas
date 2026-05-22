package com.colonel.saas.controller;

import com.colonel.saas.auth.service.AuthService;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.security.JwtAuthInterceptor;
import com.colonel.saas.security.JwtTokenProvider;
import com.colonel.saas.service.OrderSyncService;
import com.colonel.saas.service.ShortTtlCacheService;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
                .standaloneSetup(new TestController(testDataService, new ShortTtlCacheService()))
                .addInterceptors(interceptor)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        mockMvc.perform(options("/test/seed"))
                .andExpect(status().isOk());
    }

    @Test
    void seed_shouldEvictDashboardCaches() {
        TestDataService testDataService = Mockito.mock(TestDataService.class);
        ShortTtlCacheService cacheService = new ShortTtlCacheService();
        cacheService.get("dashboard:summary:admin", java.time.Duration.ofMinutes(1), () -> "stale-summary");
        cacheService.get("dashboard:metrics:admin", java.time.Duration.ofMinutes(1), () -> "stale-metrics");

        new TestController(testDataService, cacheService).seed();

        assertThat(cacheService.get("dashboard:summary:admin", java.time.Duration.ofMinutes(1), () -> "fresh-summary"))
                .isEqualTo("fresh-summary");
        assertThat(cacheService.get("dashboard:metrics:admin", java.time.Duration.ofMinutes(1), () -> "fresh-metrics"))
                .isEqualTo("fresh-metrics");
    }

    @Test
    void orderDerivedTestActions_shouldReturnServicePayloadsAndEvictCaches() {
        TestDataService testDataService = Mockito.mock(TestDataService.class);
        ShortTtlCacheService cacheService = Mockito.mock(ShortTtlCacheService.class);
        TestController controller = new TestController(testDataService, cacheService);
        OrderSyncService.SyncResult syncResult = new OrderSyncService.SyncResult(1, 2, 1, 2, 0, false);
        when(testDataService.seedAll(false)).thenReturn(Map.of("action", "seed"));
        when(testDataService.resetAll()).thenReturn(Map.of("action", "reset"));
        when(testDataService.syncTestOrders()).thenReturn(syncResult);
        when(testDataService.generateAttributedOrder()).thenReturn(Map.of("scene", "attributed"));
        when(testDataService.generateNoPickSourceOrder()).thenReturn(Map.of("scene", "no_pick_source"));
        when(testDataService.generateMissingMappingOrder()).thenReturn(Map.of("scene", "missing_mapping"));
        when(testDataService.generateAmbiguousMappingOrder()).thenReturn(Map.of("scene", "ambiguous_mapping"));
        when(testDataService.generateHistoryUnsafeOrder()).thenReturn(Map.of("scene", "history_unsafe"));
        when(testDataService.generateProductUncoveredOrder()).thenReturn(Map.of("scene", "product_uncovered"));

        assertThat(controller.seed().getData()).containsEntry("action", "seed");
        assertThat(controller.reset().getData()).containsEntry("action", "reset");
        assertThat(controller.syncOrders().getData()).isSameAs(syncResult);
        assertThat(controller.generateAttributedOrder().getData()).containsEntry("scene", "attributed");
        assertThat(controller.generateNoPickSourceOrder().getData()).containsEntry("scene", "no_pick_source");
        assertThat(controller.generateMissingMappingOrder().getData()).containsEntry("scene", "missing_mapping");
        assertThat(controller.generateAmbiguousMappingOrder().getData()).containsEntry("scene", "ambiguous_mapping");
        assertThat(controller.generateHistoryUnsafeOrder().getData()).containsEntry("scene", "history_unsafe");
        assertThat(controller.generateProductUncoveredOrder().getData()).containsEntry("scene", "product_uncovered");

        verify(cacheService, times(9)).evictByPrefix("dashboard:summary:");
        verify(cacheService, times(9)).evictByPrefix("dashboard:metrics:");
        verify(cacheService, times(9)).evictByPrefix("orders:filter-options:");
    }

    @Test
    void logisticsTestActions_shouldReturnServicePayloadWithoutEvictingOrderCaches() {
        TestDataService testDataService = Mockito.mock(TestDataService.class);
        ShortTtlCacheService cacheService = Mockito.mock(ShortTtlCacheService.class);
        TestController controller = new TestController(testDataService, cacheService);
        UUID sampleId = UUID.randomUUID();
        when(testDataService.shipSample(sampleId)).thenReturn(Map.of("action", "ship"));
        when(testDataService.signSample(sampleId)).thenReturn(Map.of("action", "sign"));

        assertThat(controller.shipSample(sampleId).getData()).containsEntry("action", "ship");
        assertThat(controller.signSample(sampleId).getData()).containsEntry("action", "sign");

        verify(cacheService, never()).evictByPrefix("dashboard:summary:");
        verify(cacheService, never()).evictByPrefix("dashboard:metrics:");
        verify(cacheService, never()).evictByPrefix("orders:filter-options:");
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

        @Bean
        ShortTtlCacheService shortTtlCacheService() {
            return new ShortTtlCacheService();
        }
    }
}
