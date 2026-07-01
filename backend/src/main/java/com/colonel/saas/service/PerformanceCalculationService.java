package com.colonel.saas.service;

import com.colonel.saas.domain.performance.application.PerformanceCalculationApplicationService;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import org.springframework.stereotype.Service;

/**
 * 业绩核心计算服务（DDD 委派壳，DDD-PERFORMANCE Slice 7）。
 *
 * <p>本类为 1-line 委派壳，所有真实业务逻辑（双轨业绩记录构建 + buildRecord + 提成计算 +
 * helper）已搬至 {@link PerformanceCalculationApplicationService}。
 * 现有调用方（{@code PerformanceBackfillService} / {@code PerformanceMonthRecalculationService} /
 * {@code OrderSyncService} / {@code CommissionService} 等）继续通过本类调用，行为零变化。</p>
 *
 * <p>性能域 facade 路由已就位：
 * <ul>
 *   <li>{@link PerformanceCalculationApplicationService} → 本 Service（caller 入口保留）</li>
 * </ul>
 */
@Service
public class PerformanceCalculationService {

    private final PerformanceCalculationApplicationService calculationApplicationService;

    public PerformanceCalculationService(
            PerformanceCalculationApplicationService calculationApplicationService) {
        this.calculationApplicationService = calculationApplicationService;
    }

    /**
     * 委派到 {@link PerformanceCalculationApplicationService#upsertFromOrder}
     * （DDD-PERFORMANCE Slice 7）。
     */
    public PerformanceRecord upsertFromOrder(ColonelsettlementOrder order) {
        return calculationApplicationService.upsertFromOrder(order);
    }
}