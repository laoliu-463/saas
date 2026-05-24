package com.colonel.saas.service;

import com.colonel.saas.entity.SampleRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class LogisticsTrackService {

    private final SampleLogisticsSyncService sampleLogisticsSyncService;

    public LogisticsTrackService(SampleLogisticsSyncService sampleLogisticsSyncService) {
        this.sampleLogisticsSyncService = sampleLogisticsSyncService;
    }

    /**
     * 刷新物流状态，若签收则推进寄样单到 PENDING_HOMEWORK。
     * 委托 {@link SampleLogisticsSyncService} 统一处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshAndProgress(SampleRequest sample) {
        if (sample == null || sample.getId() == null) {
            return;
        }
        sampleLogisticsSyncService.syncOne(sample.getId());
    }
}
