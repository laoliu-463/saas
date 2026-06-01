package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.ProductDisplayRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductLibraryRepairControllerTest {

    @Mock
    private ProductDisplayRuleService productDisplayRuleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProductLibraryRepairController controller = new ProductLibraryRepairController(productDisplayRuleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void repairActivityLibraryState_shouldCallServiceWithDryRunAndLimit() throws Exception {
        when(productDisplayRuleService.repairLibraryStateForActivity("3859423", true, 1000))
                .thenReturn(new ProductDisplayRuleService.LibraryRepairResult(
                        "3859423",
                        true,
                        1,
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        List.of(new ProductDisplayRuleService.LibraryRepairItem(
                                "3859423",
                                "99002",
                                false,
                                true,
                                "PENDING",
                                "PENDING",
                                null,
                                null,
                                1,
                                2,
                                "PENDING_AUDIT",
                                "APPROVED",
                                ProductDisplayRuleService.REPAIR_REASON_UPSTREAM_PROMOTING_AUTO_LIBRARY))));

        mockMvc.perform(post("/colonel/activities/{activityId}/products/repair-library-state", "3859423")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dryRun\":true,\"limit\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value("3859423"))
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.willSelectToLibrary").value(1))
                .andExpect(jsonPath("$.data.items[0].reason")
                        .value(ProductDisplayRuleService.REPAIR_REASON_UPSTREAM_PROMOTING_AUTO_LIBRARY));

        verify(productDisplayRuleService).repairLibraryStateForActivity("3859423", true, 1000);
    }

    @Test
    void libraryHealth_shouldReturnHealthMetrics() throws Exception {
        when(productDisplayRuleService.inspectLibraryHealth())
                .thenReturn(new ProductDisplayRuleService.LibraryHealthResult(
                        7203,
                        2597,
                        2155,
                        2155,
                        0,
                        0,
                        4606,
                        7,
                        0,
                        LocalDateTime.of(2026, 6, 1, 12, 0),
                        null));

        mockMvc.perform(get("/colonel/products/library/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotTotal").value(7203))
                .andExpect(jsonPath("$.data.promotingNotSelected").value(2155));
    }

    @Test
    void controller_shouldRequireAdminRole() {
        RequireRoles requireRoles = ProductLibraryRepairController.class.getAnnotation(RequireRoles.class);
        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).containsExactly(RoleCodes.ADMIN);
    }
}
