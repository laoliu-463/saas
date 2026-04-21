package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ExclusiveTalentService {

    private static final String KEY_RATIO_THRESHOLD = "talent.exclusive.service_fee_ratio";
    private static final String KEY_SAMPLE_THRESHOLD = "talent.exclusive.monthly_samples";
    private static final BigDecimal DEFAULT_RATIO_THRESHOLD = new BigDecimal("70");
    private static final int DEFAULT_SAMPLE_THRESHOLD = 10;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final List<Integer> EFFECTIVE_SAMPLE_STATUSES = List.of(2, 3, 4, 5, 6);

    private final JdbcTemplate jdbcTemplate;
    private final ExclusiveTalentMapper exclusiveTalentMapper;

    public ExclusiveTalentService(JdbcTemplate jdbcTemplate, ExclusiveTalentMapper exclusiveTalentMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.exclusiveTalentMapper = exclusiveTalentMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public int evaluatePreviousMonthAndApplyCurrentMonth() {
        YearMonth statsMonth = YearMonth.now().minusMonths(1);
        YearMonth applyMonth = YearMonth.now();
        return evaluateMonth(statsMonth, applyMonth);
    }

    @Transactional(rollbackFor = Exception.class)
    public int evaluateMonth(YearMonth statsMonth, YearMonth applyMonth) {
        LocalDateTime start = statsMonth.atDay(1).atStartOfDay();
        LocalDateTime end = statsMonth.plusMonths(1).atDay(1).atStartOfDay();

        BigDecimal ratioThreshold = loadDecimalConfig(KEY_RATIO_THRESHOLD, DEFAULT_RATIO_THRESHOLD);
        int sampleThreshold = loadIntConfig(KEY_SAMPLE_THRESHOLD, DEFAULT_SAMPLE_THRESHOLD);
        Map<String, Long> totalFeeByTalent = loadTalentTotalServiceFee(start, end);
        List<TalentChannelFeeRow> channelRows = loadTalentChannelServiceFee(start, end);
        Map<String, Integer> sampleCountMap = loadEffectiveSampleCount(start, end);

        int upserted = 0;
        for (TalentChannelFeeRow row : channelRows) {
            if (!StringUtils.hasText(row.talentUid()) || row.channelUserId() == null) {
                continue;
            }
            long totalFee = totalFeeByTalent.getOrDefault(row.talentUid(), 0L);
            if (totalFee <= 0L) {
                continue;
            }
            BigDecimal ratio = BigDecimal.valueOf(row.channelFee())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalFee), 2, RoundingMode.HALF_UP);
            int sampleCount = sampleCountMap.getOrDefault(sampleKey(row.channelUserId(), row.talentUid()), 0);
            if (ratio.compareTo(ratioThreshold) >= 0 && sampleCount >= sampleThreshold) {
                upsertExclusive(row, totalFee, ratio, sampleCount, applyMonth);
                upserted++;
            }
        }
        log.info("Exclusive talent monthly evaluate done, statsMonth={}, applyMonth={}, upserted={}",
                statsMonth, applyMonth, upserted);
        return upserted;
    }

    public AttributionService.ExclusiveOwner findActiveOwnerByTalentUid(String talentUid) {
        if (!StringUtils.hasText(talentUid)) {
            return null;
        }
        String month = YearMonth.now().format(MONTH_FORMATTER);
        ExclusiveTalent match = exclusiveTalentMapper.selectOne(new LambdaQueryWrapper<ExclusiveTalent>()
                .eq(ExclusiveTalent::getTalentUid, talentUid)
                .eq(ExclusiveTalent::getEffectiveMonth, month)
                .eq(ExclusiveTalent::getStatus, 1)
                .eq(ExclusiveTalent::getDeleted, 0)
                .orderByDesc(ExclusiveTalent::getCreateTime)
                .last("limit 1"));
        if (match == null || match.getUserId() == null) {
            return null;
        }
        return new AttributionService.ExclusiveOwner(match.getUserId(), match.getDeptId());
    }

    private void upsertExclusive(
            TalentChannelFeeRow row,
            long totalFee,
            BigDecimal ratio,
            int sampleCount,
            YearMonth applyMonth) {
        String effectiveMonth = applyMonth.format(MONTH_FORMATTER);
        ExclusiveTalent existing = exclusiveTalentMapper.selectOne(new LambdaQueryWrapper<ExclusiveTalent>()
                .eq(ExclusiveTalent::getTalentUid, row.talentUid())
                .eq(ExclusiveTalent::getEffectiveMonth, effectiveMonth)
                .eq(ExclusiveTalent::getDeleted, 0)
                .last("limit 1"));

        ExclusiveTalent target = existing == null ? new ExclusiveTalent() : existing;
        target.setTalentUid(row.talentUid());
        target.setTalentId(null);
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
            exclusiveTalentMapper.insert(target);
        } else {
            exclusiveTalentMapper.updateById(target);
        }
    }

    private Map<String, Long> loadTalentTotalServiceFee(LocalDateTime start, LocalDateTime end) {
        String sql = """
                SELECT COALESCE(extra_data->>'talent_uid', extra_data->>'author_id') AS talent_uid,
                       SUM(COALESCE(settle_colonel_commission, 0)) AS total_fee
                FROM colonelsettlement_order
                WHERE deleted = 0
                  AND create_time >= ?
                  AND create_time < ?
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

    private List<TalentChannelFeeRow> loadTalentChannelServiceFee(LocalDateTime start, LocalDateTime end) {
        String sql = """
                SELECT COALESCE(extra_data->>'talent_uid', extra_data->>'author_id') AS talent_uid,
                       channel_user_id,
                       dept_id,
                       SUM(COALESCE(settle_colonel_commission, 0)) AS channel_fee
                FROM colonelsettlement_order
                WHERE deleted = 0
                  AND create_time >= ?
                  AND create_time < ?
                  AND channel_user_id IS NOT NULL
                  AND COALESCE(extra_data->>'talent_uid', extra_data->>'author_id') IS NOT NULL
                GROUP BY COALESCE(extra_data->>'talent_uid', extra_data->>'author_id'), channel_user_id, dept_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String talentUid = rs.getString("talent_uid");
            UUID channelUserId = parseUuid(rs.getString("channel_user_id"));
            UUID deptId = parseUuid(rs.getString("dept_id"));
            long channelFee = rs.getLong("channel_fee");
            return new TalentChannelFeeRow(talentUid, channelUserId, deptId, channelFee);
        }, start, end);
    }

    private Map<String, Integer> loadEffectiveSampleCount(LocalDateTime start, LocalDateTime end) {
        String sql = """
                SELECT channel_user_id,
                       talent_uid,
                       COUNT(1) AS sample_count
                FROM sample_request
                WHERE deleted = 0
                  AND create_time >= ?
                  AND create_time < ?
                  AND status IN (2, 3, 4, 5, 6)
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

    private BigDecimal loadDecimalConfig(String key, BigDecimal defaultValue) {
        try {
            String sql = "SELECT config_value FROM system_config WHERE config_key = ? AND deleted = 0 LIMIT 1";
            String value = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString(1) : null, key);
            if (!StringUtils.hasText(value)) {
                return defaultValue;
            }
            return new BigDecimal(value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private int loadIntConfig(String key, int defaultValue) {
        try {
            String sql = "SELECT config_value FROM system_config WHERE config_key = ? AND deleted = 0 LIMIT 1";
            String value = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString(1) : null, key);
            if (!StringUtils.hasText(value)) {
                return defaultValue;
            }
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private String sampleKey(UUID channelUserId, String talentUid) {
        return channelUserId + "|" + talentUid;
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

    private record TalentChannelFeeRow(String talentUid, UUID channelUserId, UUID deptId, long channelFee) {
    }
}
