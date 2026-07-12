package com.colonel.saas.controller;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.performance.PerformanceBatchRequest;
import com.colonel.saas.dto.performance.PerformanceBatchResponse;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.dto.performance.PerformancePageResponse;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.dto.performance.PerformanceTrackSummaryDTO;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.PerformanceExportService;
import com.colonel.saas.service.PerformanceMonthRecalculationService;
import com.colonel.saas.service.PerformanceQueryService;
import com.colonel.saas.service.PerformanceSummaryService;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.performance.facade.PerformanceQueryFacade;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceControllerTest {

    @Mock
    private PerformanceQueryService performanceQueryService;
    @Mock
    private PerformanceSummaryService performanceSummaryService;
    @Mock
    private PerformanceExportService performanceExportService;
    @Mock
    private PerformanceMonthRecalculationService monthRecalculationService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private DddRefactorProperties dddRefactorProperties;
    @Mock
    private PerformanceQueryFacade performanceQueryFacade;

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
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()));
        org.mockito.Mockito.lenient().when(dddRefactorProperties.isEnabled()).thenReturn(false);
    }

    @Test
    void getByOrderId_shouldReturnDetail() {
        PerformanceDetailDTO detail = new PerformanceDetailDTO();
        detail.setOrderId("ORD-1");
        when(performanceQueryService.getByOrderId(any(), any())).thenReturn(detail);

        var response = controller.getByOrderId(
                "ORD-1",
                userId,
                deptId,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN));

        assertThat(response.getData().getOrderId()).isEqualTo("ORD-1");
    }

    @Test
    void batchGet_shouldRejectTooManyIds() {
        PerformanceBatchRequest request = new PerformanceBatchRequest();
        request.setOrderIds(java.util.stream.IntStream.range(0, 201).mapToObj(i -> "O" + i).toList());
        when(performanceQueryService.batchGet(any(), any()))
                .thenThrow(BusinessException.param("单次最多查询 200 个订单"));

        assertThatThrownBy(() -> controller.batchGet(
                request,
                userId,
                deptId,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void summary_shouldReturnDualTrack() {
        PerformanceSummaryResponse summary = new PerformanceSummaryResponse();
        PerformanceTrackSummaryDTO estimate = new PerformanceTrackSummaryDTO();
        estimate.setOrderCount(5);
        summary.setEstimate(estimate);
        summary.setEffective(new PerformanceTrackSummaryDTO());
        when(performanceSummaryService.getSummary(any(), any())).thenReturn(summary);

        var response = controller.summary(
                "pay",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                userId,
                deptId,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN));

        assertThat(response.getData().getEstimate().getOrderCount()).isEqualTo(5L);
    }

    @Test
    void list_shouldPassRequestAttributesIntoPerformanceAccessContext() {
        PerformancePageResponse page = new PerformancePageResponse();
        page.setTotal(10);
        when(performanceQueryService.list(any(), any())).thenReturn(page);

        var response = controller.list(
                null, null, null, null, null, null, null, null, null, null,
                "pay", null, null, "both", 1, 20, null, null,
                userId, deptId, DataScope.DEPT, List.of(RoleCodes.CHANNEL_LEADER));

        assertThat(response.getData().getTotal()).isEqualTo(10L);
        ArgumentCaptor<PerformanceAccessContext> contextCaptor =
                ArgumentCaptor.forClass(PerformanceAccessContext.class);
        verify(performanceQueryService).list(any(), contextCaptor.capture());
        PerformanceAccessContext context = contextCaptor.getValue();
        assertThat(context.userId()).isEqualTo(userId);
        assertThat(context.deptId()).isEqualTo(deptId);
        assertThat(context.dataScope()).isEqualTo(DataScope.DEPT);
        assertThat(context.roleCodes()).containsExactly(RoleCodes.CHANNEL_LEADER);
    }

    @Test
    void export_shouldRejectStaffRoleBeforeServiceCall() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.export(
                null, null, null, null, null, null, null, null, null, null,
                "pay", null, null, null, null, "both", null, null,
                userId, deptId, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF), response))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权导出业绩明细");

        verify(performanceExportService, never()).exportXlsx(any(), any());
    }

    @Test
    void export_shouldPassAccessContextToExportServiceAndWriteBytes() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        when(performanceExportService.exportXlsx(any(), any())).thenReturn(bytes);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(
                "ORD-1", "PROD-1", "商品", 10L, "商家", "ACT-1",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PAY_SUCC",
                "pay", null, null, null, null, "both", "payTime", "desc",
                userId, deptId, DataScope.DEPT, List.of(RoleCodes.BIZ_LEADER), response);

        ArgumentCaptor<PerformanceListQuery> queryCaptor = ArgumentCaptor.forClass(PerformanceListQuery.class);
        ArgumentCaptor<PerformanceAccessContext> contextCaptor =
                ArgumentCaptor.forClass(PerformanceAccessContext.class);
        verify(performanceExportService).exportXlsx(queryCaptor.capture(), contextCaptor.capture());
        PerformanceAccessContext context = contextCaptor.getValue();
        assertThat(context.userId()).isEqualTo(userId);
        assertThat(context.deptId()).isEqualTo(deptId);
        assertThat(context.dataScope()).isEqualTo(DataScope.DEPT);
        assertThat(context.roleCodes()).containsExactly(RoleCodes.BIZ_LEADER);
        assertThat(queryCaptor.getValue().getOrderId()).isEqualTo("ORD-1");
        assertThat(response.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getContentAsByteArray()).containsExactly(bytes);
    }
}
