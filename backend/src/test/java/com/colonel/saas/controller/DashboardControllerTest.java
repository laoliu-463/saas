package com.colonel.saas.controller;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.service.DashboardService;
import com.colonel.saas.service.ShortTtlCacheService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardControllerTest {

    private final DashboardService dashboardService = mock(DashboardService.class);
    private final DashboardController controller = new DashboardController(dashboardService, new ShortTtlCacheService());

    @Test
    void getSummaryShouldCacheIdenticalScopeRequests() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 2, 0, 0);
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        DashboardService.Summary summary = new DashboardService.Summary();
        summary.setOrderCount(12L);
        when(dashboardService.getSummary(start, end, userId, deptId, DataScope.ALL)).thenReturn(summary);

        ApiResult<DashboardService.Summary> first = controller.getSummary(start, end, userId, deptId, DataScope.ALL);
        ApiResult<DashboardService.Summary> second = controller.getSummary(start, end, userId, deptId, DataScope.ALL);

        assertThat(first.getData()).isSameAs(summary);
        assertThat(second.getData()).isSameAs(summary);
        assertThat(first.getData().getOrderCount()).isEqualTo(12L);
        verify(dashboardService, times(1)).getSummary(start, end, userId, deptId, DataScope.ALL);
    }

    @Test
    void getSummaryShouldCacheDifferentScopeRequestsIndependently() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 2, 0, 0);
        UUID adminUserId = UUID.randomUUID();
        UUID groupUserId = UUID.randomUUID();
        UUID groupDeptId = UUID.randomUUID();
        DashboardService.Summary allSummary = new DashboardService.Summary();
        allSummary.setOrderCount(12L);
        DashboardService.Summary deptSummary = new DashboardService.Summary();
        deptSummary.setOrderCount(3L);
        when(dashboardService.getSummary(start, end, adminUserId, null, DataScope.ALL))
                .thenReturn(allSummary);
        when(dashboardService.getSummary(start, end, groupUserId, groupDeptId, DataScope.DEPT))
                .thenReturn(deptSummary);

        ApiResult<DashboardService.Summary> allFirst =
                controller.getSummary(start, end, adminUserId, null, DataScope.ALL);
        ApiResult<DashboardService.Summary> deptFirst =
                controller.getSummary(start, end, groupUserId, groupDeptId, DataScope.DEPT);
        ApiResult<DashboardService.Summary> allSecond =
                controller.getSummary(start, end, adminUserId, null, DataScope.ALL);
        ApiResult<DashboardService.Summary> deptSecond =
                controller.getSummary(start, end, groupUserId, groupDeptId, DataScope.DEPT);

        assertThat(allFirst.getData()).isSameAs(allSummary);
        assertThat(allSecond.getData()).isSameAs(allSummary);
        assertThat(deptFirst.getData()).isSameAs(deptSummary);
        assertThat(deptSecond.getData()).isSameAs(deptSummary);
        assertThat(allFirst.getData().getOrderCount()).isEqualTo(12L);
        assertThat(deptFirst.getData().getOrderCount()).isEqualTo(3L);
        verify(dashboardService, times(1)).getSummary(start, end, adminUserId, null, DataScope.ALL);
        verify(dashboardService, times(1)).getSummary(start, end, groupUserId, groupDeptId, DataScope.DEPT);
    }

    @Test
    void getActivityProductsShouldConvertServicePageToApiPageResult() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 2, 0, 0);
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        DashboardService.ActivityProductItem item = new DashboardService.ActivityProductItem();
        item.setActivityId("A-1");
        item.setProductId("P-1");
        when(dashboardService.getActivityProductBreakdown(start, end, userId, deptId, DataScope.DEPT, 2, 30))
                .thenReturn(new DashboardService.ActivityProductPage(41, 2, 30, List.of(item)));

        ApiResult<PageResult<DashboardService.ActivityProductItem>> result =
                controller.getActivityProducts(2, 30, start, end, userId, deptId, DataScope.DEPT);

        assertThat(result.getData().getTotal()).isEqualTo(41);
        assertThat(result.getData().getPage()).isEqualTo(2);
        assertThat(result.getData().getSize()).isEqualTo(30);
        assertThat(result.getData().getRecords()).containsExactly(item);
    }
}
