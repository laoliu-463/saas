package com.colonel.saas.domain.talent.application;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.talent.domain.ExclusiveTalentRepository;
import com.colonel.saas.domain.talent.event.ExclusiveTalentActivatedEvent;
import com.colonel.saas.domain.talent.event.ExclusiveTalentDomainEventPublisher;
import com.colonel.saas.domain.talent.event.ExclusiveTalentExpiredEvent;
import com.colonel.saas.domain.talent.policy.ExclusiveTalentPolicy;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.entity.Talent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.mapper.TalentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 独家达人评估应用服务（DDD-TALENT-004）。
 *
 * <p>编排 {@link ExclusiveTalentRepository} +
 * {@link ExclusiveTalentPolicy} + {@link ExclusiveTalentDomainEventPublisher}
 * + {@link ConfigDomainFacade}，负责按月评估并发布
 * {@link ExclusiveTalentActivatedEvent} / {@link ExclusiveTalentExpiredEvent}。</p>
 */
@Service
public class ExclusiveTalentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ExclusiveTalentApplicationService.class);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JdbcTemplate jdbcTemplate;
    private final ExclusiveTalentRepository repository;
    private final TalentMapper talentMapper;
    private final ConfigDomainFacade configDomainFacade;
    private final ExclusiveTalentDomainEventPublisher eventPublisher;

    public ExclusiveTalentApplicationService(
            JdbcTemplate jdbcTemplate,
            ExclusiveTalentRepository repository,
            TalentMapper talentMapper,
            ConfigDomainFacade configDomainFacade,
            ExclusiveTalentDomainEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
        this.talentMapper = talentMapper;
        this.configDomainFacade = configDomainFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public int evaluatePreviousMonthAndApplyCurrentMonth() {
        return evaluateMonth(YearMonth.now().minusMonths(1), YearMonth.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public int evaluateMonth(YearMonth statsMonth, YearMonth applyMonth) {
        LocalDateTime start = statsMonth.atDay(1).atStartOfDay();
        LocalDateTime end = statsMonth.plusMonths(1).atDay(1).atStartOfDay();

        BigDecimal ratioThreshold = configDomainFacade.getExclusiveTalentFeeRatio();
        int sampleThreshold = configDomainFacade.getExclusiveTalentMonthlySamples();

        Map<String, Long> totalFeeByTalent = loadTalentTotalServiceFee(start, end);
        List<ChannelRow> channelRows = loadTalentChannelServiceFee(start, end);
        Map<String, Integer> sampleCountMap = loadEffectiveSampleCount(start, end);

        String effectiveMonth = applyMonth.format(MONTH_FORMATTER);
        Set<String> activatedKeys = new HashSet<>();
        int upserted = 0;
        for (ChannelRow row : channelRows) {
            if (!StringUtils.hasText(row.talentUid()) || row.channelUserId() == null) {
                continue;
            }
            long totalFee = totalFeeByTalent.getOrDefault(row.talentUid(), 0L);
            if (totalFee <= 0L) {
                continue;
            }
            BigDecimal ratio = ExclusiveTalentPolicy.computeRatio(row.channelFee(), totalFee);
            int sampleCount = sampleCountMap.getOrDefault(sampleKey(row.channelUserId(), row.talentUid()), 0);
            if (!ExclusiveTalentPolicy.meets(ratio, ratioThreshold, sampleCount, sampleThreshold)) {
                continue;
            }
            ExclusiveTalent saved = upsertExclusive(row, totalFee, ratio, sampleCount, applyMonth);
            if (saved != null) {
                publishActivated(saved);
                activatedKeys.add(row.talentUid());
                upserted++;
            }
        }
        // 失效：上一月还在 effective_talent 但当月评估不达标的达人
        expireMissing(applyMonth, activatedKeys);
        log.info("ExclusiveTalentApplicationService.evaluateMonth done statsMonth={} applyMonth={} upserted={}",
                statsMonth, applyMonth, upserted);
        return upserted;
    }

    private ExclusiveTalent upsertExclusive(ChannelRow row, long totalFee, BigDecimal ratio,
                                             int sampleCount, YearMonth applyMonth) {
        String effectiveMonth = applyMonth.format(MONTH_FORMATTER);
        ExclusiveTalent existing = repository.findByTalentUidAndMonth(row.talentUid(), effectiveMonth)
                .orElse(null);
        ExclusiveTalent target = existing == null ? new ExclusiveTalent() : existing;
        UUID talentId = resolveTalentId(row.talentUid());
        if (talentId == null) {
            log.warn("Skip exclusive talent upsert due to missing talent record, talentUid={}", row.talentUid());
            return null;
        }
        target.setTalentUid(row.talentUid());
        target.setTalentId(talentId);
        target.setUserId(row.channelUserId());
        target.setDeptId(row.deptId());
        target.setExclusiveType(1);
        target.setEffectiveMonth(effectiveMonth);
        target.setServiceFee(totalFee);
        target.setChannelTotalFee(row.channelFee());
        target.setServiceFeeRatio(ratio);
        target.setMonthlySamples(sampleCount);
        target.setStartDate(applyMonth.atDay(1));
        target.setEndDate(applyMonth.atEndOfMonth());
        target.setStatus(1);
        target.setTriggerType(1);
        target.setRemark("auto-evaluated");
        target.setDeleted(0);
        if (existing == null) {
            target.setId(UUID.randomUUID());
            repository.save(target);
        } else {
            repository.update(target);
        }
        return target;
    }

    private void publishActivated(ExclusiveTalent target) {
        try {
            eventPublisher.publishActivated(new ExclusiveTalentActivatedEvent(
                    target.getTalentId(),
                    target.getTalentUid(),
                    target.getUserId(),
                    target.getDeptId(),
                    target.getEffectiveMonth(),
                    target.getServiceFee(),
                    target.getChannelTotalFee(),
                    target.getServiceFeeRatio(),
                    target.getMonthlySamples(),
                    LocalDateTime.now()));
        } catch (RuntimeException ex) {
            // 事件发布异常不影响评估落库；记录 warn 留给运维
            log.warn("publishActivated failed talentUid={} effectiveMonth={}",
                    target.getTalentUid(), target.getEffectiveMonth(), ex);
        }
    }

    private void expireMissing(YearMonth applyMonth, Set<String> activatedKeys) {
        String effectiveMonth = applyMonth.format(MONTH_FORMATTER);
        List<ExclusiveTalent> existing = repository.listByEffectiveMonth(effectiveMonth);
        for (ExclusiveTalent record : existing) {
            if (record.getStatus() == null || record.getStatus() != 1) {
                continue;
            }
            if (activatedKeys.contains(record.getTalentUid())) {
                continue;
            }
            record.setStatus(0);
            repository.update(record);
            try {
                eventPublisher.publishExpired(new ExclusiveTalentExpiredEvent(
                        record.getTalentId(),
                        record.getTalentUid(),
                        record.getEffectiveMonth(),
                        LocalDateTime.now()));
            } catch (RuntimeException ex) {
                log.warn("publishExpired failed talentUid={} effectiveMonth={}",
                        record.getTalentUid(), record.getEffectiveMonth(), ex);
            }
        }
    }

    private Map<String, Long> loadTalentTotalServiceFee(LocalDateTime start, LocalDateTime end) {
        String sql = """
                SELECT COALESCE(extra_data->>'talent_uid', extra_data->>'author_id') AS talent_uid,
                       SUM(COALESCE(settle_colonel_commission, 0)) AS total_fee
                FROM colonelsettlement_order
                WHERE deleted = 0
                  AND settle_time >= ?
                  AND settle_time < ?
                  AND COALESCE(extra_data->>'talent_uid', extra_data->>'author_id') IS NOT NULL
                GROUP BY COALESCE(extra_data->>'talent_uid', extra_data->>'author_id')
                """;
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String talentUid = rs.getString("talent_uid");
            long totalFee = rs.getLong("total_fee");
            if (StringUtils.hasText(talentUid)) {
                result.put(talentUid, totalFee);
            }
        }, start, end);
        return result;
    }

    private List<ChannelRow> loadTalentChannelServiceFee(LocalDateTime start, LocalDateTime end) {
        String sql = """
                SELECT COALESCE(extra_data->>'talent_uid', extra_data->>'author_id') AS talent_uid,
                       channel_user_id,
                       dept_id,
                       SUM(COALESCE(settle_colonel_commission, 0)) AS channel_fee
                FROM colonelsettlement_order
                WHERE deleted = 0
                  AND settle_time >= ?
                  AND settle_time < ?
                  AND channel_user_id IS NOT NULL
                  AND COALESCE(extra_data->>'talent_uid', extra_data->>'author_id') IS NOT NULL
                GROUP BY COALESCE(extra_data->>'talent_uid', extra_data->>'author_id'), channel_user_id, dept_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ChannelRow(
                rs.getString("talent_uid"),
                parseUuid(rs.getString("channel_user_id")),
                parseUuid(rs.getString("dept_id")),
                rs.getLong("channel_fee")), start, end);
    }

    private Map<String, Integer> loadEffectiveSampleCount(LocalDateTime start, LocalDateTime end) {
        String sql = """
                SELECT channel_user_id, talent_uid, COUNT(1) AS sample_count
                FROM sample_request
                WHERE deleted = 0
                  AND ship_time IS NOT NULL
                  AND ship_time >= ?
                  AND ship_time < ?
                  AND channel_user_id IS NOT NULL
                  AND talent_uid IS NOT NULL
                GROUP BY channel_user_id, talent_uid
                """;
        Map<String, Integer> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            UUID channelUserId = parseUuid(rs.getString("channel_user_id"));
            String talentUid = rs.getString("talent_uid");
            int sampleCount = rs.getInt("sample_count");
            if (channelUserId != null && StringUtils.hasText(talentUid)) {
                result.put(sampleKey(channelUserId, talentUid), sampleCount);
            }
        }, start, end);
        return result;
    }

    private UUID resolveTalentId(String talentUid) {
        if (!StringUtils.hasText(talentUid)) {
            return null;
        }
        Talent talent = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, talentUid)
                .eq(Talent::getDeleted, 0)
                .last("limit 1"));
        return talent == null ? null : talent.getId();
    }

    private UUID parseUuid(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private String sampleKey(UUID channelUserId, String talentUid) {
        return channelUserId + "|" + talentUid;
    }

    private record ChannelRow(String talentUid, UUID channelUserId, UUID deptId, long channelFee) {
    }
}