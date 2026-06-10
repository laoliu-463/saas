package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 寄样生命周期服务：管理寄样单从待发货→待出单→已完成/已关闭的全流程状态流转。
 * <p>
 * 主要职责：订单驱动自动完成、超时自动关闭、乐观锁批量更新、状态变更日志记录。
 */
@Slf4j
@Service
public class SampleLifecycleService {

    /** 批量 SQL 分片大小 */
    private static final int SQL_IN_BATCH_SIZE = 200;
    /** 待发货 */
    private static final int STATUS_PENDING_SHIP = 2;
    /** 待出单（待完成作业） */
    private static final int STATUS_PENDING_HOMEWORK = 5;
    /** 已完成 */
    private static final int STATUS_COMPLETED = 6;
    /** 已关闭 */
    private static final int STATUS_CLOSED = 8;
    private final JdbcTemplate jdbcTemplate;
    private final SampleRequestMapper sampleRequestMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final SampleStatusLogService sampleStatusLogService;
    private final ConfigDomainFacade configDomainFacade;
    private final BusinessRuleConfigService businessRuleConfigService;
    private final SampleDomainEventPublisher sampleDomainEventPublisher;

    public SampleLifecycleService(
            JdbcTemplate jdbcTemplate,
            SampleRequestMapper sampleRequestMapper,
            TalentClaimMapper talentClaimMapper,
            SampleStatusLogService sampleStatusLogService,
            ConfigDomainFacade configDomainFacade,
            BusinessRuleConfigService businessRuleConfigService,
            SampleDomainEventPublisher sampleDomainEventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.sampleRequestMapper = sampleRequestMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.sampleStatusLogService = sampleStatusLogService;
        this.configDomainFacade = configDomainFacade;
        this.businessRuleConfigService = businessRuleConfigService;
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
    }

    /**
     * 订单同步后自动完成待出单寄样单。
     * <p>
     * 根据订单归属解析样本负责人，通过达人 UID + 商品 ID 匹配待出单寄样单，
     * 仅取最早一条并将其标记为已完成；完成后发布 SampleCompleted 领域事件。
     *
     * @param order 已同步的结算订单
     * @return 实际完成的寄样单数量（0 或 1）
     */
    @Transactional(rollbackFor = Exception.class)
    public int completePendingHomeworkByOrder(ColonelsettlementOrder order) {
        if (order == null || !StringUtils.hasText(order.getProductId())) {
            return 0;
        }
        UUID sampleOwnerId = resolveSampleOwnerForOrderCompletion(order);
        if (sampleOwnerId == null) {
            return 0;
        }
        String talentUid = resolveTalentUid(order);
        if (!StringUtils.hasText(talentUid)) {
            return 0;
        }
        List<UUID> requestIds = findPendingHomeworkRequestIds(
                sampleOwnerId,
                talentUid,
                order.getProductId()
        );
        if (requestIds.size() > 1) {
            requestIds = List.of(requestIds.get(0));
        }
        String remark = "auto complete by order: " + order.getOrderId();
        int completed = transitionSamples(
                requestIds,
                STATUS_PENDING_HOMEWORK,
                sample -> {
                    sample.setStatus(STATUS_COMPLETED);
                    sample.setCompleteTime(LocalDateTime.now());
                },
                STATUS_PENDING_HOMEWORK,
                STATUS_COMPLETED,
                remark
        );
        if (completed > 0) {
            for (UUID requestId : requestIds) {
                SampleRequest sample = sampleRequestMapper.selectById(requestId);
                if (sample != null && sample.getStatus() != null && sample.getStatus() == STATUS_COMPLETED) {
                    sampleDomainEventPublisher.publishSampleCompleted(
                            sample, order.getOrderId(), sample.getCompleteTime());
                }
            }
        }
        return completed;
    }

    /**
     * 自动关闭超过指定天数未出单的待出单寄样单。
     *
     * @param timeoutDays 超时天数阈值
     * @return 实际关闭的寄样单数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int autoCloseTimeoutPendingHomework(int timeoutDays) {
        LocalDateTime deadline = LocalDateTime.now().minusDays(timeoutDays);
        List<UUID> requestIds = findTimeoutPendingHomeworkRequestIds(deadline);
        String closeReason = "超时" + timeoutDays + "天未出单自动关闭";
        return closeSamples(requestIds, STATUS_PENDING_HOMEWORK, closeReason);
    }

    /**
     * 自动关闭超过指定天数未发货的待发货寄样单。
     *
     * @param timeoutDays 超时天数阈值
     * @return 实际关闭的寄样单数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int autoCloseTimeoutPendingShip(int timeoutDays) {
        LocalDateTime deadline = LocalDateTime.now().minusDays(timeoutDays);
        List<UUID> requestIds = findTimeoutPendingShipRequestIds(deadline);
        String closeReason = "超时" + timeoutDays + "天未发货自动关闭";
        return closeSamples(requestIds, STATUS_PENDING_SHIP, closeReason);
    }

    /** 使用业务规则配置的超时天数关闭待出单寄样单。 */
    @Transactional(rollbackFor = Exception.class)
    public int autoCloseTimeoutPendingHomework() {
        return autoCloseTimeoutPendingHomework(configDomainFacade.getSampleAutoCloseDays());
    }

    /** 使用业务规则配置的超时天数关闭待发货寄样单。 */
    @Transactional(rollbackFor = Exception.class)
    public int autoCloseTimeoutPendingShip() {
        return autoCloseTimeoutPendingShip(businessRuleConfigService.getSampleTimeoutPendingShipDays());
    }

    /**
     * 将指定状态的寄样单批量关闭，关闭后发布 SampleClosed 领域事件。
     *
     * @param requestIds 待关闭的寄样单 ID 列表
     * @param fromStatus 期望的当前状态
     * @param closeReason 关闭原因
     * @return 实际关闭数量
     */
    private int closeSamples(List<UUID> requestIds, int fromStatus, String closeReason) {
        LocalDateTime now = LocalDateTime.now();
        int closed = transitionSamples(
                requestIds,
                fromStatus,
                sample -> {
                    sample.setStatus(STATUS_CLOSED);
                    sample.setCloseTime(now);
                    sample.setCloseReason(closeReason);
                },
                fromStatus,
                STATUS_CLOSED,
                closeReason
        );
        if (closed > 0) {
            for (UUID requestId : requestIds) {
                SampleRequest sample = sampleRequestMapper.selectById(requestId);
                if (sample != null && sample.getStatus() != null && sample.getStatus() == STATUS_CLOSED) {
                    sampleDomainEventPublisher.publishSampleClosed(sample, closeReason, sample.getCloseTime());
                }
            }
        }
        return closed;
    }

    /**
     * 寄样单状态流转核心方法：加载、应用变更、批量持久化、写入状态日志。
     *
     * @param requestIds 目标寄样单 ID 列表
     * @param expectedStatus 期望的当前状态（乐观锁前置过滤）
     * @param mutator 对每个寄样单执行的状态变更操作
     * @param logFromStatus 日志记录的起始状态
     * @param logToStatus 日志记录的目标状态
     * @param remark 操作备注
     * @return 实际流转数量
     */
    private int transitionSamples(
            List<UUID> requestIds,
            int expectedStatus,
            Consumer<SampleRequest> mutator,
            int logFromStatus,
            int logToStatus,
            String remark) {
        List<SampleRequest> samples = loadSamplesInStatus(requestIds, expectedStatus);
        if (samples.isEmpty()) {
            return 0;
        }
        List<SampleStatusLogService.LogEntry> logEntries = new ArrayList<>(samples.size());
        for (SampleRequest sample : samples) {
            mutator.accept(sample);
            logEntries.add(new SampleStatusLogService.LogEntry(sample.getId(), logFromStatus, logToStatus, null, remark));
        }
        batchUpdateSamples(samples);
        sampleStatusLogService.logBatch(logEntries);
        return samples.size();
    }

    /**
     * 分片批量更新寄样单，使用乐观锁（version 字段）防止并发冲突。
     * <p>
     * JDBC batchUpdate 失败时回退到逐条 MyBatis Plus updateById，
     * 任一记录乐观锁冲突则抛出 BusinessException。
     */
    private void batchUpdateSamples(List<SampleRequest> samples) {
        if (samples == null || samples.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (SampleRequest sample : samples) {
            sample.setUpdateTime(now);
        }
        for (List<SampleRequest> batch : partition(samples, SQL_IN_BATCH_SIZE)) {
            int[][] updated = jdbcTemplate.batchUpdate("""
                    UPDATE sample_request
                    SET status = ?, complete_time = ?, close_time = ?, close_reason = ?, update_time = ?,
                        version = COALESCE(version, 0) + 1
                    WHERE id = ? AND deleted = 0 AND COALESCE(version, 0) = ?
                    """,
                    batch,
                    batch.size(),
                    (ps, sample) -> {
                        ps.setObject(1, sample.getStatus());
                        ps.setObject(2, sample.getCompleteTime());
                        ps.setObject(3, sample.getCloseTime());
                        ps.setObject(4, sample.getCloseReason());
                        ps.setObject(5, sample.getUpdateTime());
                        ps.setObject(6, sample.getId());
                        ps.setObject(7, sample.getVersion() == null ? 0 : sample.getVersion());
                    }
            );
            if (updated == null || updated.length != batch.size()) {
                for (SampleRequest sample : batch) {
                    OptimisticLockSupport.requireUpdated(sampleRequestMapper.updateById(sample));
                    sample.setVersion((sample.getVersion() == null ? 0 : sample.getVersion()) + 1);
                }
                return;
            }
            for (int i = 0; i < batch.size(); i++) {
                int affected = updated[i].length == 0 ? 0 : updated[i][0];
                if (affected == 0) {
                    throw BusinessException.conflict(
                            "寄样单状态已被他人修改，请刷新后重试: " + batch.get(i).getRequestNo());
                }
                SampleRequest sample = batch.get(i);
                sample.setVersion((sample.getVersion() == null ? 0 : sample.getVersion()) + 1);
            }
        }
    }

    /** 分片加载寄样单，仅返回状态匹配 expectedStatus 的记录。 */
    private List<SampleRequest> loadSamplesInStatus(List<UUID> requestIds, int expectedStatus) {
        if (requestIds == null || requestIds.isEmpty()) {
            return List.of();
        }
        List<SampleRequest> matched = new ArrayList<>();
        for (List<UUID> batch : partition(requestIds, SQL_IN_BATCH_SIZE)) {
            for (SampleRequest sample : sampleRequestMapper.selectBatchIds(batch)) {
                if (sample != null && sample.getStatus() != null && sample.getStatus() == expectedStatus) {
                    matched.add(sample);
                }
            }
        }
        return matched;
    }

    /**
     * 解析订单对应的寄样单负责人。
     * <p>
     * 优先使用达人认领记录（TalentClaim）匹配归属用户；
     * 无匹配认领时回退到订单自带的 userId/channelUserId。
     */
    private UUID resolveSampleOwnerForOrderCompletion(ColonelsettlementOrder order) {
        UUID attributedOwner = order.getUserId() != null ? order.getUserId() : order.getChannelUserId();
        if (attributedOwner == null) {
            return null;
        }
        UUID talentId = order.getTalentId();
        if (talentId == null) {
            return attributedOwner;
        }
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        if (activeClaims == null || activeClaims.isEmpty()) {
            return attributedOwner;
        }
        boolean matchesClaimOwner = activeClaims.stream()
                .anyMatch(claim -> attributedOwner.equals(claim.getUserId()));
        if (matchesClaimOwner) {
            return attributedOwner;
        }
        return activeClaims.stream()
                .filter(claim -> claim.getUserId() != null)
                .max(Comparator.comparing(
                        TalentClaim::getClaimedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(TalentClaim::getUserId)
                .orElse(attributedOwner);
    }

    /** 按负责人 + 达人 UID + 商品 ID 查询待出单寄样单，仅取最早一条。 */
    private List<UUID> findPendingHomeworkRequestIds(UUID channelUserId, String talentUid, String sourceProductId) {
        String sql = """
                SELECT sr.id
                FROM sample_request sr
                JOIN product p ON p.id = sr.product_id
                WHERE sr.deleted = 0
                  AND p.deleted = 0
                  AND sr.status = 5
                  AND sr.channel_user_id = ?
                  AND sr.talent_uid = ?
                  AND p.product_id = ?
                ORDER BY sr.create_time ASC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> parseUuid(rs.getString("id")), channelUserId, talentUid, sourceProductId)
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** 查询待出单状态中超过 deadline 的寄样单 ID，按创建时间升序。 */
    private List<UUID> findTimeoutPendingHomeworkRequestIds(LocalDateTime deadline) {
        String sql = """
                SELECT sr.id
                FROM sample_request sr
                WHERE sr.deleted = 0
                  AND sr.status = 5
                  AND COALESCE(sr.deliver_time, sr.update_time, sr.create_time) < ?
                ORDER BY sr.create_time ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> parseUuid(rs.getString("id")), deadline)
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** 查询待发货状态中超过 deadline 的寄样单 ID，按创建时间升序。 */
    private List<UUID> findTimeoutPendingShipRequestIds(LocalDateTime deadline) {
        String sql = """
                SELECT sr.id
                FROM sample_request sr
                WHERE sr.deleted = 0
                  AND sr.status = 2
                  AND COALESCE(sr.audit_time, sr.update_time, sr.create_time) < ?
                ORDER BY sr.create_time ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> parseUuid(rs.getString("id")), deadline)
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** 从订单的 extraData 中提取达人 UID，优先 talent_uid，回退 author_id。 */
    private String resolveTalentUid(ColonelsettlementOrder order) {
        Map<String, Object> extra = order.getExtraData();
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        String talentUid = asText(extra.get("talent_uid"));
        if (StringUtils.hasText(talentUid)) {
            return talentUid;
        }
        return asText(extra.get("author_id"));
    }

    /** null 安全的 toString 包装。 */
    private String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    /** 将字符串解析为 UUID，空白或解析失败返回 null。 */
    private UUID parseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    /** 将集合按指定大小分片，用于批量 SQL 操作。 */
    private <T> List<List<T>> partition(Collection<T> values, int batchSize) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<T> list = values instanceof List<T> typed ? typed : List.copyOf(values);
        List<List<T>> partitions = new ArrayList<>();
        for (int index = 0; index < list.size(); index += batchSize) {
            partitions.add(list.subList(index, Math.min(index + batchSize, list.size())));
        }
        return partitions;
    }
}
