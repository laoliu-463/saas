package com.colonel.saas.controller;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.performance.PerformanceBatchRequest;
import com.colonel.saas.dto.performance.PerformanceBatchResponse;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                performanceQueryFacade);
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
    void list_shouldReturnPage() {
        PerformancePageResponse page = new PerformancePageResponse();
        page.setTotal(10);
        when(performanceQueryService.list(any(), any())).thenReturn(page);

        var response = controller.list(
                null, null, null, null, null, null, null, null, null, null,
                "pay", null, null, "both", 1, 20, null, null,
                userId, deptId, DataScope.ALL, List.of(RoleCodes.ADMIN));

        assertThat(response.getData().getTotal()).isEqualTo(10L);
    }
}
