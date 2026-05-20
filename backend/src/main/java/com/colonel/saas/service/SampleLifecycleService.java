package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
public class SampleLifecycleService {

    private static final int SQL_IN_BATCH_SIZE = 200;
    private static final int STATUS_PENDING_SHIP = 2;
    private static final int STATUS_PENDING_HOMEWORK = 5;
    private static final int STATUS_COMPLETED = 6;
    private static final int STATUS_CLOSED = 8;
    private final JdbcTemplate jdbcTemplate;
    private final SampleRequestMapper sampleRequestMapper;
    private final SampleStatusLogService sampleStatusLogService;
    private final BusinessRuleConfigService businessRuleConfigService;

    public SampleLifecycleService(
            JdbcTemplate jdbcTemplate,
            SampleRequestMapper sampleRequestMapper,
            SampleStatusLogService sampleStatusLogService,
            BusinessRuleConfigService businessRuleConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sampleRequestMapper = sampleRequestMapper;
        this.sampleStatusLogService = sampleStatusLogService;
        this.businessRuleConfigService = businessRuleConfigService;
    }

    @Transactional(rollbackFor = Exception.class)
    public int completePendingHomeworkByOrder(ColonelsettlementOrder order) {
        if (order == null || order.getChannelUserId() == null || !StringUtils.hasText(order.getProductId())) {
            return 0;
        }
        String talentUid = resolveTalentUid(order);
        if (!StringUtils.hasText(talentUid)) {
            return 0;
        }
        List<UUID> requestIds = findPendingHomeworkRequestIds(
                order.getChannelUserId(),
                talentUid,
                order.getProductId()
        );
        String remark = "auto complete by order: " + order.getOrderId();
        return transitionSamples(
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
    }

    @Transactional(rollbackFor = Exception.class)
    public int autoCloseTimeoutPendingHomework(int timeoutDays) {
        LocalDateTime deadline = LocalDateTime.now().minusDays(timeoutDays);
        List<UUID> requestIds = findTimeoutPendingHomeworkRequestIds(deadline);
        String closeReason = "超时" + timeoutDays + "天未出单自动关闭";
        return closeSamples(requestIds, STATUS_PENDING_HOMEWORK, closeReason);
    }

    @Transactional(rollbackFor = Exception.class)
    public int autoCloseTimeoutPendingShip(int timeoutDays) {
        LocalDateTime deadline = LocalDateTime.now().minusDays(timeoutDays);
        List<UUID> requestIds = findTimeoutPendingShipRequestIds(deadline);
        String closeReason = "超时" + timeoutDays + "天未发货自动关闭";
        return closeSamples(requestIds, STATUS_PENDING_SHIP, closeReason);
    }

    @Transactional(rollbackFor = Exception.class)
    public int autoCloseTimeoutPendingHomework() {
        return autoCloseTimeoutPendingHomework(businessRuleConfigService.getSampleTimeoutHomeworkDays());
    }

    @Transactional(rollbackFor = Exception.class)
    public int autoCloseTimeoutPendingShip() {
        return autoCloseTimeoutPendingShip(businessRuleConfigService.getSampleTimeoutPendingShipDays());
    }

    private int closeSamples(List<UUID> requestIds, int fromStatus, String closeReason) {
        LocalDateTime now = LocalDateTime.now();
        return transitionSamples(
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
    }

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
            sampleRequestMapper.updateById(sample);
            logEntries.add(new SampleStatusLogService.LogEntry(sample.getId(), logFromStatus, logToStatus, null, remark));
        }
        sampleStatusLogService.logBatch(logEntries);
        return samples.size();
    }

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
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> parseUuid(rs.getString("id")), channelUserId, talentUid, sourceProductId)
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }

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

    private String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

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
