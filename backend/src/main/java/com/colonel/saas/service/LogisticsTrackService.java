package com.colonel.saas.service;

import com.colonel.saas.entity.SampleRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物流追踪薄包装服务，委托 {@link SampleLogisticsSyncService} 完成实际物流同步与状态推进。
 *
 * <p>职责：将寄样单的物流刷新请求统一转发给 {@link SampleLogisticsSyncService#syncOne}，
 * 由其完成快递100查询、签收判断、状态流转（PENDING_HOMEWORK）等核心逻辑。</p>
 *
 * <ul>
 *   <li>提供 {@link #refreshAndProgress} 触发单条寄样单的物流刷新与状态推进</li>
 * </ul>
 *
 * <p><b>业务领域：</b>寄样域 — 物流追踪</p>
 * <p><b>协作关系：</b>委托 {@link SampleLogisticsSyncService} 执行物流查询和状态流转</p>
 *
 * @see SampleLogisticsSyncService
 */
@Slf4j
@Service
public class LogisticsTrackService {

    /** 寄样物流同步服务，负责实际的快递查询与状态推进 */
    private final SampleLogisticsSyncService sampleLogisticsSyncService;

    public LogisticsTrackService(SampleLogisticsSyncService sampleLogisticsSyncService) {
        this.sampleLogisticsSyncService = sampleLogisticsSyncService;
    }

    /**
     * 刷新物流状态，若签收则推进寄样单到 PENDING_HOMEWORK。
     *
     * <ol>
     *   <li>校验寄样单对象和 ID 不为空</li>
     *   <li>委托 {@link SampleLogisticsSyncService#syncOne} 完成物流查询与状态推进</li>
     * </ol>
     *
     * @param sample 寄样单实体，包含寄样单 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshAndProgress(SampleRequest sample) {
        if (sample == null || sample.getId() == null) {
            return;
        }
        sampleLogisticsSyncService.syncOne(sample.getId());
    }
}
