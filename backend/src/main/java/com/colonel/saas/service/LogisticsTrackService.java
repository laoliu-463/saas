package com.colonel.saas.service;

import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.colonel.saas.mapper.SampleRequestMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class LogisticsTrackService {

    private static final int STATUS_SHIPPING = 3;
    private static final int STATUS_PENDING_HOMEWORK = 5;

    private final LogisticsGateway logisticsGateway;
    private final SampleRequestMapper sampleRequestMapper;
    private final SampleStatusLogService sampleStatusLogService;

    public LogisticsTrackService(
            LogisticsGateway logisticsGateway,
            SampleRequestMapper sampleRequestMapper,
            SampleStatusLogService sampleStatusLogService) {
        this.logisticsGateway = logisticsGateway;
        this.sampleRequestMapper = sampleRequestMapper;
        this.sampleStatusLogService = sampleStatusLogService;
    }

    /**
     * 刷新物流状态，若签收则推进寄样单到 PENDING_HOMEWORK。
     * 幂等：已处于 PENDING_HOMEWORK 的单据不会重复推进。
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshAndProgress(SampleRequest sample) {
        String trackingNo = sample.getTrackingNo();
        String shipperCode = sample.getShipperCode();

        if (!StringUtils.hasText(trackingNo) || !StringUtils.hasText(shipperCode)) {
            log.debug("Skip logistics refresh for sample {} — missing trackingNo or shipperCode", sample.getRequestNo());
            return;
        }

        if (sample.getStatus() == null || sample.getStatus() != STATUS_SHIPPING) {
            log.debug("Skip logistics refresh for sample {} — status {} is not SHIPPING", sample.getRequestNo(), sample.getStatus());
            return;
        }

        LogisticsGateway.LogisticsTrackResult result;
        try {
            result = logisticsGateway.queryTrack(shipperCode, trackingNo);
        } catch (Exception ex) {
            log.error("Logistics query failed for sample {} (trackingNo={}, shipperCode={})",
                    sample.getRequestNo(), trackingNo, shipperCode, ex);
            return;
        }

        if (!result.success()) {
            log.warn("Logistics query returned failure for sample {}: {}", sample.getRequestNo(), result.reason());
            return;
        }

        if (!"SIGNED".equals(result.internalStatus())) {
            log.debug("Logistics status for sample {} is {}, not SIGNED — no progression",
                    sample.getRequestNo(), result.internalStatus());
            return;
        }

        // 幂等检查
        if (sample.getStatus() != STATUS_SHIPPING) {
            log.debug("Sample {} already progressed past SHIPPING (status={}), skip", sample.getRequestNo(), sample.getStatus());
            return;
        }

        LocalDateTime signedAt = result.signedAt() != null ? result.signedAt() : LocalDateTime.now();
        int fromStatus = sample.getStatus();
        sample.setStatus(STATUS_PENDING_HOMEWORK);
        sample.setDeliverTime(signedAt);
        OptimisticLockSupport.requireUpdated(sampleRequestMapper.updateById(sample));

        UUID operatorId = sample.getUserId() != null ? sample.getUserId() : sample.getId();
        sampleStatusLogService.log(sample.getId(), fromStatus, STATUS_PENDING_HOMEWORK, operatorId,
                "物流签收自动推进");

        log.info("Sample {} progressed from SHIPPING to PENDING_HOMEWORK (signed at {})",
                sample.getRequestNo(), signedAt);
    }
}
