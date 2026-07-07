package com.colonel.saas.domain.sample.facade;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.SampleLifecycleService;
import org.springframework.stereotype.Service;

/**
 * 寄样交作业 Facade 遗留实现，委派现有寄样生命周期服务保持行为不变。
 */
@Service
public class LegacySampleHomeworkFacade implements SampleHomeworkFacade {

    private final SampleLifecycleService sampleLifecycleService;

    public LegacySampleHomeworkFacade(SampleLifecycleService sampleLifecycleService) {
        this.sampleLifecycleService = sampleLifecycleService;
    }

    @Override
    public int completePendingHomeworkByOrder(ColonelsettlementOrder order) {
        return sampleLifecycleService.completePendingHomeworkByOrder(order);
    }
}
