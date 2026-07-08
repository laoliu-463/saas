package com.colonel.saas.service;

import com.colonel.saas.domain.sample.application.LogisticsTrackApplicationService;
import com.colonel.saas.entity.SampleRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 物流追踪薄包装服务（DDD 委派壳，DDD-LOGISTICS-001 Slice 2）。
 *
 * <p>本类为 1-line 委派壳，所有真实业务已搬至
 * {@link LogisticsTrackApplicationService}。现有调用方（{@code LogisticsTrackJob}）
 * 继续通过本类调用，行为零变化。</p>
 */
@Service
public class LogisticsTrackService {

    private final LogisticsTrackApplicationService applicationService;

    public LogisticsTrackService(@Lazy LogisticsTrackApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 刷新物流状态——1-line delegate（DDD-LOGISTICS-001 Slice 2）。
     */
    public void refreshAndProgress(SampleRequest sample) {
        applicationService.refreshAndProgress(sample);
    }

    /**
     * 批量刷新发货中寄样单——1-line delegate（DDD-LOGISTICS-001 Slice 2）。
     */
    public SampleLogisticsSyncService.SyncBatchSummary refreshShippingSamples() {
        return applicationService.refreshShippingSamples();
    }
}
