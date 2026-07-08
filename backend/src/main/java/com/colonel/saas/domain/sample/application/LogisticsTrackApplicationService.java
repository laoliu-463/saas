package com.colonel.saas.domain.sample.application;

import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.service.SampleLogisticsSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物流追踪薄包装 Application Service（DDD-LOGISTICS-001 Slice 2）。
 *
 * <p>从 {@code service.LogisticsTrackService} 整体迁移（仅 2 public method + 1 构造器）：
 * <ul>
 *   <li>{@link #refreshAndProgress} —— 触发单条寄样单物流刷新与状态推进</li>
 *   <li>{@link #refreshShippingSamples} —— 触发发货中寄样单批量刷新</li>
 * </ul>
 *
 * <p>本类承接 Service 的薄包装业务逻辑，委托 {@link SampleLogisticsSyncService}
 * 完成快递100查询与状态流转核心逻辑。</p>
 */
@Slf4j
@Service
public class LogisticsTrackApplicationService {

    private final SampleLogisticsSyncService sampleLogisticsSyncService;

    public LogisticsTrackApplicationService(SampleLogisticsSyncService sampleLogisticsSyncService) {
        this.sampleLogisticsSyncService = sampleLogisticsSyncService;
    }

    /**
     * 刷新物流状态，若签收则推进寄样单到 PENDING_HOMEWORK。
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshAndProgress(SampleRequest sample) {
        if (sample == null || sample.getId() == null) {
            return;
        }
        sampleLogisticsSyncService.syncOne(sample.getId());
    }

    /**
     * 刷新所有发货中寄样单的物流状态。
     */
    public SampleLogisticsSyncService.SyncBatchSummary refreshShippingSamples() {
        return sampleLogisticsSyncService.refreshShippingSamples();
    }
}
