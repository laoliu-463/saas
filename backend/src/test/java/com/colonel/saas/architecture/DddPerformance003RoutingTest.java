package com.colonel.saas.architecture;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.controller.PerformanceController;
import com.colonel.saas.domain.performance.facade.PerformanceQueryFacade;
import com.colonel.saas.dto.performance.PerformanceBatchRequest;
import com.colonel.saas.dto.performance.PerformanceBatchResponse;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformancePageResponse;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.PerformanceExportService;
import com.colonel.saas.service.PerformanceMonthRecalculationService;
import com.colonel.saas.service.PerformanceQueryService;
import com.colonel.saas.service.PerformanceSummaryService;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DddPerformance003RoutingTest {

    @Mock private PerformanceQueryService performanceQueryService;
    @Mock private PerformanceSummaryService performanceSummaryService;
    @Mock private PerformanceExportService performanceExportService;
    @Mock private PerformanceMonthRecalculationService monthRecalculationService;
    @Mock private OperationLogService operationLogService;
    @Mock private DddRefactorProperties dddRefactorProperties;
    @Mock private DddRefactorProperties.Switch performanceQuerySwitch;
    @Mock private PerformanceQueryFacade performanceQueryFacade;

    private PerformanceController controller;
    private final UUID userId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new PerformanceController(
                performanceQueryService,
                performanceSummaryService,
                performanceExportService,
                monthRecalculationService,
                operationLogService,
                dddRefactorProperties,
                performanceQueryFacade,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy())
        );
    }

    private void enableFacadeSwitch() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getPerformanceQuery()).thenReturn(performanceQuerySwitch);
        when(performanceQuerySwitch.isEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("开关关闭时，单笔查询直接调用遗留Service")
    void shouldDelegateToQueryServiceWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        PerformanceDetailDTO expected = new PerformanceDetailDTO();
        when(performanceQueryService.getByOrderId(eq("ORD-1"), any(PerformanceAccessContext.class))).thenReturn(expected);

        var response = controller.getByOrderId("ORD-1", userId, deptId, DataScope.ALL, List.of("admin"));

        assertThat(response.getData()).isSameAs(expected);
        verify(performanceQueryFacade, never()).getByOrderId(any(), any());
        verify(performanceQueryService).getByOrderId(eq("ORD-1"), any(PerformanceAccessContext.class));
    }

    @Test
    @DisplayName("开关开启时，单笔查询路由至PerformanceQueryFacade")
    void shouldRouteToFacadeWhenSwitchOn() {
        enableFacadeSwitch();
        PerformanceDetailDTO expected = new PerformanceDetailDTO();
        when(performanceQueryFacade.getByOrderId(eq("ORD-1"), any(PerformanceAccessContext.class))).thenReturn(expected);

        var response = controller.getByOrderId("ORD-1", userId, deptId, DataScope.ALL, List.of("admin"));

        assertThat(response.getData()).isSameAs(expected);
        verify(performanceQueryService, never()).getByOrderId(any(), any());
        verify(performanceQueryFacade).getByOrderId(eq("ORD-1"), any(PerformanceAccessContext.class));
    }

    @Test
    @DisplayName("开关关闭时，批量查询调用遗留Service")
    void shouldDelegateBatchToServiceWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        PerformanceBatchResponse expected = new PerformanceBatchResponse();
        when(performanceQueryService.batchGet(any(), any())).thenReturn(expected);

        PerformanceBatchRequest req = new PerformanceBatchRequest();
        req.setOrderIds(List.of("ORD-1"));
        var response = controller.batchGet(req, userId, deptId, DataScope.ALL, List.of("admin"));

        assertThat(response.getData()).isSameAs(expected);
        verify(performanceQueryFacade, never()).batchGet(any(), any());
    }

    @Test
    @DisplayName("开关开启时，批量查询路由至PerformanceQueryFacade")
    void shouldRouteBatchToFacadeWhenSwitchOn() {
        enableFacadeSwitch();
        PerformanceBatchResponse expected = new PerformanceBatchResponse();
        when(performanceQueryFacade.batchGet(any(), any())).thenReturn(expected);

        PerformanceBatchRequest req = new PerformanceBatchRequest();
        req.setOrderIds(List.of("ORD-1"));
        var response = controller.batchGet(req, userId, deptId, DataScope.ALL, List.of("admin"));

        assertThat(response.getData()).isSameAs(expected);
        verify(performanceQueryService, never()).batchGet(any(), any());
    }

    @Test
    @DisplayName("开关关闭时，分页查询调用遗留Service")
    void shouldDelegateListToServiceWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        PerformancePageResponse expected = new PerformancePageResponse();
        when(performanceQueryService.list(any(), any())).thenReturn(expected);

        var response = controller.list(
                null, null, null, null, null, null, null, null, null, null,
                "pay", null, null, "both", 1, 20, null, null,
                userId, deptId, DataScope.ALL, List.of("admin"));

        assertThat(response.getData()).isSameAs(expected);
        verify(performanceQueryFacade, never()).list(any(), any());
    }

    @Test
    @DisplayName("开关开启时，分页查询路由至PerformanceQueryFacade")
    void shouldRouteListToFacadeWhenSwitchOn() {
        enableFacadeSwitch();
        PerformancePageResponse expected = new PerformancePageResponse();
        when(performanceQueryFacade.list(any(), any())).thenReturn(expected);

        var response = controller.list(
                null, null, null, null, null, null, null, null, null, null,
                "pay", null, null, "both", 1, 20, null, null,
                userId, deptId, DataScope.ALL, List.of("admin"));

        assertThat(response.getData()).isSameAs(expected);
        verify(performanceQueryService, never()).list(any(), any());
    }

    @Test
    @DisplayName("开关关闭时，汇总统计调用遗留Service")
    void shouldDelegateSummaryToServiceWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        PerformanceSummaryResponse expected = new PerformanceSummaryResponse();
        when(performanceSummaryService.getSummary(any(), any())).thenReturn(expected);

        var response = controller.summary(
                "pay", null, null, null, null, null, null, null, null, null, null, null,
                userId, deptId, DataScope.ALL, List.of("admin"));

        assertThat(response.getData()).isSameAs(expected);
        verify(performanceQueryFacade, never()).getSummary(any(), any());
    }

    @Test
    @DisplayName("开关开启时，汇总统计路由至PerformanceQueryFacade")
    void shouldRouteSummaryToFacadeWhenSwitchOn() {
        enableFacadeSwitch();
        PerformanceSummaryResponse expected = new PerformanceSummaryResponse();
        when(performanceQueryFacade.getSummary(any(), any())).thenReturn(expected);

        var response = controller.summary(
                "pay", null, null, null, null, null, null, null, null, null, null, null,
                userId, deptId, DataScope.ALL, List.of("admin"));

        assertThat(response.getData()).isSameAs(expected);
        verify(performanceSummaryService, never()).getSummary(any(), any());
    }
}
