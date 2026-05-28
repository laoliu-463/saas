package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import com.colonel.saas.mapper.TalentMapper;
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

/**
 * 独家达人评估服务，按月评估渠道-达人关系是否满足独家条件并写入 exclusive_talents 表。
 *
 * <p>核心逻辑：统计指定月份内每位渠道员在每个达人上的服务费收入，计算占该达人总收入的比例，
 * 同时要求当月寄样数量达到阈值，双重条件均满足则认定为独家达人关系并在下月生效。</p>
 *
 * <ul>
 *   <li>提供 {@link #evaluatePreviousMonthAndApplyCurrentMonth} 评估上月数据并应用到当月</li>
 *   <li>提供 {@link #evaluateMonth} 评估指定月份数据并应用到目标月份</li>
 *   <li>提供 {@link #findActiveOwnerByTalentUid} 查询达人当月独家归属人</li>
 *   <li>双重阈值：服务费比例阈值（默认 70%）+ 月寄样数量阈值（默认 10 件）</li>
 * </ul>
 *
 * <p><b>业务领域：</b>配置域 — 独家达人评估</p>
 * <p><b>协作关系：</b>依赖 {@link ExclusiveTalentMapper} 持久化独家达人记录；
 * 依赖 {@link TalentMapper} 解析达人 UID 对应的 talentId；
 * 依赖 {@link JdbcTemplate} 执行原生 SQL 聚合查询</p>
 *
 * @see ExclusiveTalentMapper
 * @see ExclusiveMerchantService
 */
@Slf4j
@Service
public class ExclusiveTalentService {

    /** 系统配置键：达人独家服务费比例阈值 */
    private static final String KEY_RATIO_THRESHOLD = "talent.exclusive.service_fee_ratio";

    /** 系统配置键：达人独家月寄样数量阈值 */
    private static final String KEY_SAMPLE_THRESHOLD = "talent.exclusive.monthly_samples";

    /** 默认服务费比例阈值：70% */
    private static final BigDecimal DEFAULT_RATIO_THRESHOLD = new BigDecimal("70");

    /** 默认月寄样数量阈值：10 件 */
    private static final int DEFAULT_SAMPLE_THRESHOLD = 10;

    /** 月份格式化器，用于格式化 "yyyy-MM" */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /** JDBC 模板，执行原生 SQL 聚合查询 */
    private final JdbcTemplate jdbcTemplate;

    /** 独家达人 Mapper，操作 exclusive_talents 表 */
    private final ExclusiveTalentMapper exclusiveTalentMapper;

    /** 达人 Mapper，用于解析达人 UID 到 talentId */
    private final TalentMapper talentMapper;

    public ExclusiveTalentService(
            JdbcTemplate jdbcTemplate,
            ExclusiveTalentMapper exclusiveTalentMapper,
            TalentMapper talentMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.exclusiveTalentMapper = exclusiveTalentMapper;
        this.talentMapper = talentMapper;
    }

    /**
     * 评估上月数据并应用独家达人结果到当月。
     *
     * @return 成功写入/更新的独家达人记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int evaluatePreviousMonthAndApplyCurrentMonth() {
        YearMonth statsMonth = YearMonth.now().minusMonths(1);
        YearMonth applyMonth = YearMonth.now();
        return evaluateMonth(statsMonth, applyMonth);
    }

    /**
     * 评估指定月份的独家达人状态并写入目标月份的独家记录。
     *
     * <ol>
     *   <li>第一步：加载服务费比例阈值（默认 70%）和月寄样数量阈值（默认 10 件）</li>
     *   <li>第二步：查询每位达人的总服务费收入</li>
     *   <li>第三步：查询每个渠道-达人组合的服务费收入</li>
     *   <li>第四步：查询每个渠道-达人的有效寄样数量</li>
     *   <li>第五步：计算比例，同时满足服务费比例和寄样数量两个阈值则写入独家记录</li>
     * </ol>
     *
     * @param statsMonth 统计月份（数据来源月份）
     * @param applyMonth 生效月份（独家记录生效的月份）
     * @return 成功写入/更新的独家达人记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int evaluateMonth(YearMonth statsMonth, YearMonth applyMonth) {
        // 计算统计月份的时间范围
        LocalDateTime start = statsMonth.atDay(1).atStartOfDay();
        LocalDateTime end = statsMonth.plusMonths(1).atDay(1).atStartOfDay();

        // 第一步：加载双阈值配置
        BigDecimal ratioThreshold = loadDecimalConfig(KEY_RATIO_THRESHOLD, DEFAULT_RATIO_THRESHOLD);
        int sampleThreshold = loadIntConfig(KEY_SAMPLE_THRESHOLD, DEFAULT_SAMPLE_THRESHOLD);
        // 第二步：查询每位达人的总服务费收入
        Map<String, Long> totalFeeByTalent = loadTalentTotalServiceFee(start, end);
        // 第三步：查询每个渠道-达人组合的服务费收入
        List<TalentChannelFeeRow> channelRows = loadTalentChannelServiceFee(start, end);
        // 第四步：查询每个渠道-达人的有效寄样数量
        Map<String, Integer> sampleCountMap = loadEffectiveSampleCount(start, end);

        int upserted = 0;
        // 第五步：逐条计算，同时满足服务费比例和寄样数量两个阈值则写入独家记录
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

    /**
     * 查询达人当月独家归属人（渠道员），用于归因时优先分配给独家持有人。
     *
     * @param talentUid 达人抖音 UID
     * @return 独家归属人信息（用户 ID + 部门 ID），无独家记录时返回 null
     */
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

    /**
     * 写入或更新独家达人记录（upsert），以 talentUid + effectiveMonth 为去重键。
     *
     * <p>注意：若 talentUid 对应的达人记录不存在（resolveTalentId 返回 null），则跳过写入。</p>
     */
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
        UUID talentId = resolveTalentId(row.talentUid());
        if (talentId == null) {
            log.warn("Skip exclusive talent upsert due to missing talent record, talentUid={}", row.talentUid());
            return;
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
            exclusiveTalentMapper.insert(target);
        } else {
            exclusiveTalentMapper.updateById(target);
        }
    }

    /**
     * 查询指定时间范围内每位达人的总服务费收入（按 talentUid 分组聚合）。
     */
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

    /**
     * 查询指定时间范围内每个渠道-达人组合的服务费收入（按 talentUid + channelUserId + deptId 分组聚合）。
     */
    private List<TalentChannelFeeRow> loadTalentChannelServiceFee(LocalDateTime start, LocalDateTime end) {
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
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String talentUid = rs.getString("talent_uid");
            UUID channelUserId = parseUuid(rs.getString("channel_user_id"));
            UUID deptId = parseUuid(rs.getString("dept_id"));
            long channelFee = rs.getLong("channel_fee");
            return new TalentChannelFeeRow(talentUid, channelUserId, deptId, channelFee);
        }, start, end);
    }

    /**
     * 查询指定时间范围内每个渠道-达人组合的有效寄样数量（已发货的寄样请求，按 channelUserId + talentUid 分组聚合）。
     */
    private Map<String, Integer> loadEffectiveSampleCount(LocalDateTime start, LocalDateTime end) {
        String sql = """
                SELECT channel_user_id,
                       talent_uid,
                       COUNT(1) AS sample_count
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

    /**
     * 从 system_config 表加载指定键的 BigDecimal 配置值，读取失败或为空时返回默认值。
     */
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

    /**
     * 从 system_config 表加载指定键的 int 配置值，读取失败或为空时返回默认值。
     */
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

    /**
     * 构建渠道-达人组合键，格式为 "{channelUserId}|{talentUid}"，用于在 Map 中聚合寄样数量。
     */
    private String sampleKey(UUID channelUserId, String talentUid) {
        return channelUserId + "|" + talentUid;
    }

    /**
     * null 安全的 UUID 解析，解析失败返回 null。
     */
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

    /**
     * 根据达人抖音 UID 解析对应的 talentId，达人记录不存在时返回 null。
     */
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

    /** 渠道-达人服务费行记录 */
    private record TalentChannelFeeRow(String talentUid, UUID channelUserId, UUID deptId, long channelFee) {
    }
}
