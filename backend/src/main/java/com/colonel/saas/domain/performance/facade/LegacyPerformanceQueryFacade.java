package com.colonel.saas.domain.performance.facade;

import com.colonel.saas.dto.performance.PerformanceBatchResponse;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.dto.performance.PerformancePageResponse;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.service.PerformanceQueryService;
import com.colonel.saas.service.PerformanceSummaryService;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link PerformanceQueryFacade} 遗留委派实现：
 * 将调用委派给既有 {@link PerformanceQueryService} 和 {@link PerformanceSummaryService}，保证零行为变更。
 */
@Service
public class LegacyPerformanceQueryFacade implements PerformanceQueryFacade {

    private final PerformanceQueryService performanceQueryService;
    private final PerformanceSummaryService performanceSummaryService;

    public LegacyPerformanceQueryFacade(
            PerformanceQueryService performanceQueryService,
            PerformanceSummaryService performanceSummaryService) {
        this.performanceQueryService = performanceQueryService;
        this.performanceSummaryService = performanceSummaryService;
    }

    @Override
    public PerformanceDetailDTO getByOrderId(String orderId, PerformanceAccessContext context) {
        return performanceQueryService.getByOrderId(orderId, context);
    }

    @Override
    public PerformanceBatchResponse batchGet(List<String> orderIds, PerformanceAccessContext context) {
        return performanceQueryService.batchGet(orderIds, context);
    }

    @Override
    public PerformancePageResponse list(PerformanceListQuery query, PerformanceAccessContext context) {
        return performanceQueryService.list(query, context);
    }

    @Override
    public PerformanceSummaryResponse getSummary(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        return performanceSummaryService.getSummary(query, context);
    }
}
