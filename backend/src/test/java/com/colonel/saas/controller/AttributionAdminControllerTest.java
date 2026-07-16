package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.AttributionOwnerReconciliationService;
import com.colonel.saas.service.OperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AttributionAdminControllerTest {

    @Mock private AttributionOwnerReconciliationService reconciliationService;
    @Mock private OperationLogService operationLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AttributionAdminController(
                        reconciliationService, operationLogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void reconcileEndpointShouldBeAdminOnlyAndForwardRequest() throws Exception {
        AttributionOwnerReconciliationService.ReconcileResult result =
                new AttributionOwnerReconciliationService.ReconcileResult(1, 1, 0, 0, true, List.of());
        when(reconciliationService.reconcile(any())).thenReturn(result);
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/order-attribution/admin/mapping-owner-reconcile")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dryRun\":true,\"limit\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.scanned").value(1));

        verify(reconciliationService).reconcile(any());
        verify(operationLogService).recordSystemAction(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("订单归因"),
                org.mockito.ArgumentMatchers.eq("分类历史推广链接归属类型"),
                org.mockito.ArgumentMatchers.eq("POST"),
                org.mockito.ArgumentMatchers.eq("attribution_owner_reconcile"),
                any(),
                org.mockito.ArgumentMatchers.eq("dry-run"),
                any());

        Method endpoint = AttributionAdminController.class.getDeclaredMethod(
                "reconcileMappingOwners", AttributionOwnerReconciliationService.ReconcileRequest.class, UUID.class);
        RequireRoles roles = endpoint.getAnnotation(RequireRoles.class);
        assertThat(roles.value()).containsExactly(RoleCodes.ADMIN);
    }

    @Test
    void reconcileAuditShouldIdentifyExplicitMappingIds() throws Exception {
        AttributionOwnerReconciliationService.ReconcileResult result =
                new AttributionOwnerReconciliationService.ReconcileResult(1, 1, 0, 0, true, List.of());
        when(reconciliationService.reconcile(any())).thenReturn(result);
        UUID userId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        mockMvc.perform(post("/api/order-attribution/admin/mapping-owner-reconcile")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mappingIds\":[\"" + mappingId + "\"],\"dryRun\":true,\"limit\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<String> targetIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationLogService).recordSystemAction(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("订单归因"),
                org.mockito.ArgumentMatchers.eq("分类历史推广链接归属类型"),
                org.mockito.ArgumentMatchers.eq("POST"),
                org.mockito.ArgumentMatchers.eq("attribution_owner_reconcile"),
                targetIdCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("dry-run"),
                any());
        assertThat(targetIdCaptor.getValue()).isEqualTo(mappingId.toString());
    }
}
