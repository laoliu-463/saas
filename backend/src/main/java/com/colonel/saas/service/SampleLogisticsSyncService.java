package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.SampleLogisticsTrace;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryResult;
import com.colonel.saas.gateway.logistics.query.LogisticsStatusCode;
import com.colonel.saas.mapper.SampleLogisticsTraceMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 寄样物流状态同步服务。
 * <p>
 * 负责通过物流查询网关拉取最新物流轨迹，持久化轨迹记录，
 * 并在物流签收时自动推进寄样申请状态至待作业。
 * 支持单条同步、按物流单号同步、批量轮询同步三种模式。
 * </p>
 *
 * <ul>
 *     <li>单条寄样物流同步（{@link #syncOne}）</li>
 *     <li>按物流单号查找并同步（{@link #syncByTrackingNo}）</li>
 *     <li>批量轮询在途寄样并同步（{@link #syncPendingInTransit}）</li>
 *     <li>物流签收后自动推进寄样状态至待作业（{@link #handleLogisticsResult}）</li>
 *     <li>物流轨迹持久化与去重（基于 SHA-256 节点哈希）</li>
 * </ul>
 *
 * <p><b>业务域：</b>寄样域 — 物流状态同步</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link LogisticsQueryGateway} — 物流查询网关（按供应商适配）</li>
 *     <li>{@link SampleRequestMapper} — 寄样申请数据访问</li>
 *     <li>{@link SampleLogisticsTraceMapper} — 物流轨迹数据访问</li>
 *     <li>{@link SampleStatusLogService} — 寄样状态变更日志</li>
 *     <li>{@link SampleDomainEventPublisher} — 寄样领域事件发布</li>
 * </ul>
 *
 * @see LogisticsQueryGateway
 * @see SampleLogisticsTraceMapper
 */
@Slf4j
@Service
public class SampleLogisticsSyncService {

    private static final int STATUS_PENDING_SHIP = 2;
    private static final int STATUS_SHIPPING = 3;
    private static final int STATUS_PENDING_HOMEWORK = 5;

    /** 物流查询网关 */
    private final LogisticsQueryGateway logisticsQueryGateway;
    /** 寄样申请数据访问 */
    private final SampleRequestMapper sampleRequestMapper;
    /** 物流轨迹数据访问 */
    private final SampleLogisticsTraceMapper sampleLogisticsTraceMapper;
    /** 寄样状态变更日志服务 */
    private final SampleStatusLogService sampleStatusLogService;
    /** 寄样领域事件发布器 */
    private final SampleDomainEventPublisher sampleDomainEventPublisher;
    /** 编程式事务操作 */
    private final TransactionOperations transactionOperations;

    public SampleLogisticsSyncService(
            LogisticsQueryGateway logisticsQueryGateway,
            SampleRequestMapper sampleRequestMapper,
            SampleLogisticsTraceMapper sampleLogisticsTraceMapper,
            SampleStatusLogService sampleStatusLogService,
            SampleDomainEventPublisher sampleDomainEventPublisher,
            TransactionOperations transactionOperations) {
        this.logisticsQueryGateway = logisticsQueryGateway;
        this.sampleRequestMapper = sampleRequestMapper;
        this.sampleLogisticsTraceMapper = sampleLogisticsTraceMapper;
        this.sampleStatusLogService = sampleStatusLogService;
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
        this.transactionOperations = transactionOperations;
    }

    /**
     * 同步单条寄样申请的物流状态。
     * <p>根据寄样申请 ID 查询物流轨迹并持久化更新。</p>
     *
     * @param sampleRequestId 寄样申请 ID
     * @return 物流查询结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LogisticsQueryResult syncOne(UUID sampleRequestId) {
        SampleRequest sample = sampleRequestMapper.selectById(sampleRequestId);
        if (sample == null) {
            return LogisticsQueryResult.queryFailed(
                    logisticsQueryGateway.providerName(), null, null, "NOT_FOUND", "寄样申请不存在");
        }
        return syncAndPersist(sample);
    }

    /**
     * 按物流单号查找寄样申请并同步物流状态。
     * <p>按更新时间倒序取最新匹配记录。</p>
     *
     * @param trackingNo 物流单号
     * @return 物流查询结果
     */
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

    /**
     * 批量轮询在途寄样并同步物流状态。
     * <p>处理流程：</p>
     * <ol>
     *     <li>查询状态为待发货或运输中、且有物流单号的寄样申请</li>
     *     <li>筛选最近30分钟未查询过或最近6小时未收到回调的记录</li>
     *     <li>逐条调用物流查询网关并持久化更新</li>
     *     <li>汇总成功、失败、跳过计数</li>
     * </ol>
     *
     * @param batchSize 批次大小
     * @return 批量同步汇总（总数、成功数、失败数、跳过数）
     */
    public SyncBatchSummary syncPendingInTransit(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();
        wrapper.in("status", STATUS_PENDING_SHIP, STATUS_SHIPPING)
                .isNotNull("tracking_no")
                .ne("tracking_no", "")
                .and(w -> w.isNull("logistics_last_query_at")
                        .or()
                        .le("logistics_last_query_at", now.minusMinutes(30)))
                .and(w -> w.isNull("logistics_last_callback_at")
                        .or()
                        .le("logistics_last_callback_at", now.minusHours(6)))
                .orderByAsc("logistics_last_query_at")
                .last("LIMIT " + Math.max(1, batchSize));
        List<SampleRequest> samples = sampleRequestMapper.selectList(wrapper);

        int success = 0;
        int failed = 0;
        int skipped = 0;
        for (SampleRequest sample : samples) {
            try {
                LogisticsQueryResult result = syncAndPersistBatchItem(sample);
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

    /**
     * 处理物流查询结果并持久化到寄样申请。
     * <p>处理流程：</p>
     * <ol>
     *     <li>更新寄样申请的物流状态、状态名称、原始报文</li>
     *     <li>查询失败时记录错误信息并返回</li>
     *     <li>查询成功时替换轨迹记录</li>
     *     <li>若物流已签收，自动推进寄样状态至待作业</li>
     * </ol>
     *
     * @param sample 寄样申请实体
     * @param result 物流查询结果
     * @return 处理后的物流查询结果
     */
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

    /**
     * 查询指定寄样申请的物流轨迹列表。
     *
     * @param sampleRequestId 寄样申请 ID
     * @return 物流轨迹列表，按轨迹时间倒序排列
     */
    public List<SampleLogisticsTrace> listTraces(UUID sampleRequestId) {
        return sampleLogisticsTraceMapper.selectList(new LambdaQueryWrapper<SampleLogisticsTrace>()
                .eq(SampleLogisticsTrace::getSampleRequestId, sampleRequestId)
                .orderByDesc(SampleLogisticsTrace::getTraceTime));
    }

    /**
     * 内部同步并持久化物流结果（事务内调用）。
     *
     * @param sample 寄样申请实体
     * @return 物流查询结果
     */
    private LogisticsQueryResult syncAndPersist(SampleRequest sample) {
        if (!StringUtils.hasText(sample.getTrackingNo())) {
            return LogisticsQueryResult.queryFailed(
                    logisticsQueryGateway.providerName(),
                    sample.getShipperCode(),
                    sample.getTrackingNo(),
                    "NO_TRACKING",
                    "缺少物流单号");
        }
        LogisticsQueryResult result = queryLogistics(sample);
        return handleLogisticsResult(sample, result);
    }

    /**
     * 批量同步单条记录（使用编程式事务包装）。
     *
     * @param sample 寄样申请实体
     * @return 物流查询结果
     */
    private LogisticsQueryResult syncAndPersistBatchItem(SampleRequest sample) {
        if (!StringUtils.hasText(sample.getTrackingNo())) {
            return LogisticsQueryResult.queryFailed(
                    logisticsQueryGateway.providerName(),
                    sample.getShipperCode(),
                    sample.getTrackingNo(),
                    "NO_TRACKING",
                    "缺少物流单号");
        }
        LogisticsQueryResult result = queryLogistics(sample);
        LogisticsQueryResult persisted = transactionOperations.execute(status -> handleLogisticsResult(sample, result));
        return persisted == null ? result : persisted;
    }

    /**
     * 调用物流查询网关查询物流轨迹。
     * <p>快递公司编码为空时默认使用 AUTO。</p>
     *
     * @param sample 寄样申请实体
     * @return 物流查询结果
     */
    private LogisticsQueryResult queryLogistics(SampleRequest sample) {
        String company = StringUtils.hasText(sample.getShipperCode()) ? sample.getShipperCode() : "AUTO";
        return logisticsQueryGateway.query(LogisticsTrackCommand.builder()
                .companyCode(company)
                .trackingNo(sample.getTrackingNo())
                .phone(sample.getRecipientPhone())
                .to(sample.getRecipientAddress())
                .build());
    }

    /**
     * 物流签收后自动推进寄样状态。
     * <p>仅当寄样申请处于待发货或运输中状态时生效：
     * 记录签收时间，将状态推进至待作业（STATUS_PENDING_HOMEWORK），
     * 记录状态变更日志并发布签收领域事件。</p>
     *
     * @param sample   寄样申请实体
     * @param signedAt 签收时间
     */
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

    /**
     * 替换寄样申请的物流轨迹记录。
     * <p>先删除旧轨迹，再逐条插入新轨迹节点，每条轨迹包含 SHA-256 节点哈希用于去重。</p>
     *
     * @param sample 寄样申请实体
     * @param result 物流查询结果（包含轨迹列表）
     */
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
            trace.setLocation(item.getLocation());
            trace.setNodeHash(nodeHash(sample, result, item));
            trace.setRawPayload(result.getRawPayload());
            trace.setCreatedAt(now);
            sampleLogisticsTraceMapper.insert(trace);
        }
    }

    /**
     * 生成物流轨迹节点的 SHA-256 哈希值。
     * <p>由快递公司编码、物流单号、轨迹时间、轨迹内容、位置、状态码拼接后计算哈希，用于去重。</p>
     *
     * @param sample 寄样申请实体
     * @param result 物流查询结果
     * @param item   单条轨迹节点
     * @return SHA-256 十六进制哈希字符串
     */
    private String nodeHash(
            SampleRequest sample,
            LogisticsQueryResult result,
            LogisticsQueryResult.LogisticsTraceItem item) {
        String raw = String.join("|",
                nullToEmpty(sample.getShipperCode()).trim().toUpperCase(),
                nullToEmpty(sample.getTrackingNo()),
                item.getTraceTime() == null ? "" : item.getTraceTime().toString(),
                nullToEmpty(item.getTraceContent()),
                nullToEmpty(item.getLocation()),
                result.getStatusCode() == null ? "" : result.getStatusCode().name());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("生成物流轨迹节点哈希失败", e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void putExtraValue(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new HashMap<>()
                : new HashMap<>(sample.getExtraData());
        extra.put(key, value);
        sample.setExtraData(extra);
    }

    /**
     * 持久化寄样申请，使用乐观锁校验更新行数。
     *
     * @param sample 寄样申请实体
     */
    private void persistSample(SampleRequest sample) {
        OptimisticLockSupport.requireUpdated(sampleRequestMapper.updateById(sample));
    }

    /**
     * 批量物流同步汇总结果。
     *
     * @param total   处理总数
     * @param success 成功数
     * @param failed  失败数
     * @param skipped 跳过数（未配置物流网关）
     */
    public record SyncBatchSummary(int total, int success, int failed, int skipped) {
    }
}
