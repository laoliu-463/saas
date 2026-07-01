package com.colonel.saas.service;

import com.colonel.saas.domain.performance.application.PerformanceSummaryApplicationService;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.dto.performance.PerformanceTrackSummaryDTO;
import org.springframework.stereotype.Service;

/**
 * 业绩指标卡片双轨汇总服务（DDD 委派壳，DDD-PERFORMANCE Slice 4）。
 *
 * <p>本类为 1-line 委派壳，所有真实业务逻辑（SQL 装配 / 数据权限范围 / 类型转换）已搬至
 * {@link PerformanceSummaryApplicationService}。现有调用方
 * （{@code PerformanceController} / {@code LegacyPerformanceQueryFacade} /
 * {@code LegacyOrderPerformanceQueryFacade} / {@code PerformanceCacheWarmupJob}）
 * 继续通过本类调用，行为零变化。</p>
 *
 * <p>公开方法 {@code getSummary} / {@code aggregateEstimate} / {@code aggregateEffective}
 * 均委派至 Application 层；保留为 public 是为了不破坏既有反射 / 测试直访 / Facade 委派。</p>
 */
@Service
public class PerformanceSummaryService {

    private final PerformanceSummaryApplicationService summaryApplicationService;

    public PerformanceSummaryService(PerformanceSummaryApplicationService summaryApplicationService) {
        this.summaryApplicationService = summaryApplicationService;
    }

    /**
     * 委派到 {@link PerformanceSummaryApplicationService#getSummary}（DDD-PERFORMANCE Slice 4）。
     */
    public PerformanceSummaryResponse getSummary(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        return summaryApplicationService.getSummary(query, context);
    }

    /**
     * 委派到 {@link PerformanceSummaryApplicationService#aggregateEstimate}（DDD-PERFORMANCE Slice 4）。
     */
    public PerformanceTrackSummaryDTO aggregateEstimate(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        return summaryApplicationService.aggregateEstimate(query, context);
    }

    /**
     * 委派到 {@link PerformanceSummaryApplicationService#aggregateEffective}（DDD-PERFORMANCE Slice 4）。
     */
    public PerformanceTrackSummaryDTO aggregateEffective(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        return summaryApplicationService.aggregateEffective(query, context);
    }
}