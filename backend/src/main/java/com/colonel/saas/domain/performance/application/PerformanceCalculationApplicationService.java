package com.colonel.saas.domain.performance.application;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.service.PerformanceCalculationService;
import org.springframework.stereotype.Service;

/**
 * Performance calculation application entrypoint.
 *
 * <p>The legacy calculation service still owns the calculation algorithm; this
 * application service is the domain-facing orchestration boundary used by
 * listeners and batch jobs.</p>
 */
@Service
public class PerformanceCalculationApplicationService {

    private final PerformanceCalculationService performanceCalculationService;

    public PerformanceCalculationApplicationService(PerformanceCalculationService performanceCalculationService) {
        this.performanceCalculationService = performanceCalculationService;
    }

    public PerformanceRecord upsertFromOrder(ColonelsettlementOrder order) {
        return performanceCalculationService.upsertFromOrder(order);
    }
}
