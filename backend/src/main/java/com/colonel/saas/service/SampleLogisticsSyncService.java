package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.SampleLogisticsTrace;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryResult;
import com.colonel.saas.gateway.logistics.query.LogisticsStatusCode;
import com.colonel.saas.mapper.SampleLogisticsTraceMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class SampleLogisticsSyncService {

    private static final int STATUS_PENDING_SHIP = 2;
    private static final int STATUS_SHIPPING = 3;
    private static final int STATUS_PENDING_HOMEWORK = 5;

    private final LogisticsQueryGateway logisticsQueryGateway;
    private final SampleRequestMapper sampleRequestMapper;
    private final SampleLogisticsTraceMapper sampleLogisticsTraceMapper;
    private final SampleStatusLogService sampleStatusLogService;
    private final SampleDomainEventPublisher sampleDomainEventPublisher;

    public SampleLogisticsSyncService(
            LogisticsQueryGateway logisticsQueryGateway,
            SampleRequestMapper sampleRequestMapper,
            SampleLogisticsTraceMapper sampleLogisticsTraceMapper,
            SampleStatusLogService sampleStatusLogService,
            SampleDomainEventPublisher sampleDomainEventPublisher) {
        this.logisticsQueryGateway = logisticsQueryGateway;
        this.sampleRequestMapper = sampleRequestMapper;
        this.sampleLogisticsTraceMapper = sampleLogisticsTraceMapper;
        this.sampleStatusLogService = sampleStatusLogService;
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public LogisticsQueryResult syncOne(UUID sampleRequestId) {
        SampleRequest sample = sampleRequestMapper.selectById(sampleRequestId);
        if (sample == null) {
            return LogisticsQueryResult.queryFailed(
                    logisticsQueryGateway.providerName(), null, null, "NOT_FOUND", "寄样申请不存在");
        }
        return syncAndPersist(sample);
    }

    @Transactional(rollbackFor = Exception.class)
    public LogisticsQueryResult syncByTrackingNo(String trackingNo) {
        if (!StringUtils.hasText(trackingNo)) {
            return LogisticsQueryResult.queryFailed(
                    logisticsQueryGateway.providerName(), null, trackingNo, "INVALID_PARAM", "物流单号不能为空");
        }
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getTrackingNo, trackingNo.trim())
                .orderByDesc(SampleRequest::getUpdateTime)
                .last("LIMIT 1"));
        if (sample == null) {
            return LogisticsQueryResult.queryFailed(
                    logisticsQueryGateway.providerName(), null, trackingNo, "NOT_FOUND", "未找到寄样单");
        }
        return syncAndPersist(sample);
    }

    public SyncBatchSummary syncPendingInTransit(int batchSize) {
        LambdaQueryWrapper<SampleRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SampleRequest::getStatus, STATUS_PENDING_SHIP, STATUS_SHIPPING)
                .isNotNull(SampleRequest::getTrackingNo)
                .ne(SampleRequest::getTrackingNo, "")
                .orderByAsc(SampleRequest::getLogisticsLastQueryAt)
                .last("LIMIT " + Math.max(1, batchSize));
        List<SampleRequest> samples = sampleRequestMapper.selectList(wrapper);

        int success = 0;
        int failed = 0;
        int skipped = 0;
        for (SampleRequest sample : samples) {
            try {
                LogisticsQueryResult result = syncAndPersist(sample);
                if (result.isSuccess()) {
                    success++;
                } else if (LogisticsStatusCode.NOT_CONFIGURED == result.getStatusCode()) {
                    skipped++;
                } else {
                    failed++;
                }
            } catch (Exception ex) {
                failed++;
                log.error("Sample logistics sync failed for requestNo={}", sample.getRequestNo(), ex);
            }
        }
        return new SyncBatchSummary(samples.size(), success, failed, skipped);
    }

    @Transactional(rollbackFor = Exception.class)
    public LogisticsQueryResult handleLogisticsResult(SampleRequest sample, LogisticsQueryResult result) {
        if (sample == null || result == null) {
            return result;
        }
        LocalDateTime now = LocalDateTime.now();
        sample.setLogisticsLastQueryAt(now);
        sample.setLogisticsStatus(result.getStatusCode() == null ? null : result.getStatusCode().name());
        sample.setLogisticsStatusName(result.getStatusName());
        sample.setLogisticsRawPayload(result.getRawPayload());

        if (!result.isSuccess()) {
            sample.setLogisticsLastError(result.getErrorMessage());
            persistSample(sample);
            return result;
        }

        sample.setLogisticsLastError(null);
        replaceTraces(sample, result);

        if (result.isSigned()) {
            applySigned(sample, result.getSignedAt() != null ? result.getSignedAt() : now);
        }
        persistSample(sample);
        return result;
    }

    public List<SampleLogisticsTrace> listTraces(UUID sampleRequestId) {
        return sampleLogisticsTraceMapper.selectList(new LambdaQueryWrapper<SampleLogisticsTrace>()
                .eq(SampleLogisticsTrace::getSampleRequestId, sampleRequestId)
                .orderByDesc(SampleLogisticsTrace::getTraceTime));
    }

    private LogisticsQueryResult syncAndPersist(SampleRequest sample) {
        if (!StringUtils.hasText(sample.getTrackingNo())) {
            return LogisticsQueryResult.queryFailed(
                    logisticsQueryGateway.providerName(),
                    sample.getShipperCode(),
                    sample.getTrackingNo(),
                    "NO_TRACKING",
                    "缺少物流单号");
        }
        String company = StringUtils.hasText(sample.getShipperCode()) ? sample.getShipperCode() : "AUTO";
        LogisticsQueryResult result = logisticsQueryGateway.query(company, sample.getTrackingNo());
        return handleLogisticsResult(sample, result);
    }

    private void applySigned(SampleRequest sample, LocalDateTime signedAt) {
        Integer status = sample.getStatus();
        if (status == null) {
            return;
        }
        if (status != STATUS_SHIPPING && status != STATUS_PENDING_SHIP) {
            return;
        }
        int fromStatus = status;
        sample.setSignedAt(signedAt);
        sample.setDeliverTime(signedAt);
        sample.setStatus(STATUS_PENDING_HOMEWORK);
        putExtraValue(sample, "logisticsSource", logisticsQueryGateway.providerName());
        UUID operatorId = sample.getUserId() != null ? sample.getUserId() : sample.getId();
        sampleStatusLogService.log(sample.getId(), fromStatus, STATUS_PENDING_HOMEWORK, operatorId, "物流签收自动推进");
        sampleDomainEventPublisher.publishSampleSigned(sample, signedAt);
        log.info("Sample {} auto progressed to PENDING_HOMEWORK after signed", sample.getRequestNo());
    }

    private void replaceTraces(SampleRequest sample, LogisticsQueryResult result) {
        sampleLogisticsTraceMapper.delete(new LambdaQueryWrapper<SampleLogisticsTrace>()
                .eq(SampleLogisticsTrace::getSampleRequestId, sample.getId()));
        if (result.getTraces() == null || result.getTraces().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (LogisticsQueryResult.LogisticsTraceItem item : result.getTraces()) {
            SampleLogisticsTrace trace = new SampleLogisticsTrace();
            trace.setId(UUID.randomUUID());
            trace.setSampleRequestId(sample.getId());
            trace.setTrackingNo(sample.getTrackingNo());
            trace.setLogisticsCompany(sample.getShipperCode());
            trace.setStatusCode(result.getStatusCode() == null ? null : result.getStatusCode().name());
            trace.setStatusName(result.getStatusName());
            trace.setTraceTime(item.getTraceTime());
            trace.setTraceContent(item.getTraceContent());
            trace.setRawPayload(result.getRawPayload());
            trace.setCreatedAt(now);
            sampleLogisticsTraceMapper.insert(trace);
        }
    }

    private void putExtraValue(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new HashMap<>()
                : new HashMap<>(sample.getExtraData());
        extra.put(key, value);
        sample.setExtraData(extra);
    }

    private void persistSample(SampleRequest sample) {
        OptimisticLockSupport.requireUpdated(sampleRequestMapper.updateById(sample));
    }

    public record SyncBatchSummary(int total, int success, int failed, int skipped) {
    }
}
