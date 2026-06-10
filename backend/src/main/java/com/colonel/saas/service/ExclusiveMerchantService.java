package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 独家商家评估服务，按月评估商家是否满足独家条件并写入 exclusive_merchants 表。
 *
 * <p>核心逻辑：统计指定月份内每位招商员在每个商家的服务费收入，计算商家服务费占该招商员
 * 总收入的比例，达到阈值则认定为独家商家并在下月生效。</p>
 *
 * <ul>
 *   <li>提供 {@link #evaluatePreviousMonthAndApplyCurrentMonth} 评估上月数据并应用到当月</li>
 *   <li>提供 {@link #evaluateMonth} 评估指定月份数据并应用到目标月份</li>
 *   <li>提供 {@link #findActiveOwnerByMerchantId} 查询商家当月独家归属人</li>
 *   <li>阈值从 system_config 表读取，默认 70%</li>
 * </ul>
 *
 * <p><b>业务领域：</b>配置域 — 独家商家评估</p>
 * <p><b>协作关系：</b>依赖 {@link ExclusiveMerchantMapper} 持久化独家商家记录；
 * 依赖 {@link ConfigDomainFacade} 读取独家服务费比例阈值（DDD-CONFIG-003）；
 * 依赖 {@link JdbcTemplate} 执行原生 SQL 聚合查询</p>
 *
 * @see ExclusiveMerchantMapper
 * @see ExclusiveMerchantQueryService
 */
@Slf4j
@Service
public class ExclusiveMerchantService {

    /** 月份格式化器，用于格式化 "yyyy-MM" */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 配置域门面，读取独家商家服务费比例阈值 */
    private final ConfigDomainFacade configDomainFacade;

    /** JDBC 模板，执行原生 SQL 聚合查询 */
    private final JdbcTemplate jdbcTemplate;

    /** 独家商家 Mapper，操作 exclusive_merchants 表 */
    private final ExclusiveMerchantMapper exclusiveMerchantMapper;

    public ExclusiveMerchantService(ConfigDomainFacade configDomainFacade,
                                    JdbcTemplate jdbcTemplate,
                                    ExclusiveMerchantMapper exclusiveMerchantMapper) {
        this.configDomainFacade = configDomainFacade;
        this.jdbcTemplate = jdbcTemplate;
        this.exclusiveMerchantMapper = exclusiveMerchantMapper;
    }

    /**
     * 评估上月数据并应用独家商家结果到当月。
     *
     * @return 成功写入/更新的独家商家记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int evaluatePreviousMonthAndApplyCurrentMonth() {
        YearMonth statsMonth = YearMonth.now().minusMonths(1);
        YearMonth applyMonth = YearMonth.now();
        return evaluateMonth(statsMonth, applyMonth);
    }

    /**
     * 评估指定月份的商家独家状态并写入目标月份的独家记录。
     *
     * <ol>
     *   <li>第一步：从 system_config 加载服务费比例阈值（默认 70%）</li>
     *   <li>第二步：查询统计月份内每位招商员的总服务费收入</li>
     *   <li>第三步：查询统计月份内每个商家-招商员组合的服务费收入</li>
     *   <li>第四步：计算商家服务费占招商员总收入的比例，达到阈值则写入独家记录</li>
     * </ol>
     *
     * @param statsMonth 统计月份（数据来源月份）
     * @param applyMonth 生效月份（独家记录生效的月份）
     * @return 成功写入/更新的独家商家记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int evaluateMonth(YearMonth statsMonth, YearMonth applyMonth) {
        // 第一步：加载服务费比例阈值
        BigDecimal threshold = loadRatioThreshold();

        // 第二步：查询每位招商员的总服务费收入
        List<UserTotalFeeRow> userTotalFeeRows = loadUserTotalFee(statsMonth);
        Map<UUID, Long> totalByUser = userTotalFeeRows.stream()
                .collect(Collectors.toMap(UserTotalFeeRow::userId, UserTotalFeeRow::totalFee));

        // 第三步：查询每个商家-招商员组合的服务费收入
        List<MerchantUserFeeRow> merchantRows = loadMerchantUserFee(statsMonth);
        int upserted = 0;
        // 第四步：逐条计算比例，达到阈值则写入独家记录
        for (MerchantUserFeeRow row : merchantRows) {
            if (!StringUtils.hasText(row.merchantId()) || row.userId() == null) {
                continue;
            }
            long totalFee = totalByUser.getOrDefault(row.userId(), 0L);
            if (totalFee <= 0L) {
                continue;
            }
            BigDecimal ratio = BigDecimal.valueOf(row.merchantFee())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalFee), 2, RoundingMode.HALF_UP);
            if (ratio.compareTo(threshold) >= 0) {
                upsertExclusive(row, totalFee, ratio, applyMonth);
                upserted++;
            }
        }
        log.info("Exclusive merchant monthly evaluate done, statsMonth={}, applyMonth={}, upserted={}",
                statsMonth, applyMonth, upserted);
        return upserted;
    }

    /**
     * 查询商家当月独家归属人（招商员），用于归因时优先分配给独家持有人。
     *
     * @param merchantId 商家 ID
     * @return 独家归属人信息（用户 ID + 部门 ID），无独家记录时返回 null
     */
    public AttributionService.ExclusiveOwner findActiveOwnerByMerchantId(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        String month = YearMonth.now().format(MONTH_FORMATTER);
        ExclusiveMerchant match = exclusiveMerchantMapper.selectOne(new LambdaQueryWrapper<ExclusiveMerchant>()
                .eq(ExclusiveMerchant::getMerchantId, merchantId)
                .eq(ExclusiveMerchant::getEffectiveMonth, month)
                .eq(ExclusiveMerchant::getStatus, 1)
                .eq(ExclusiveMerchant::getDeleted, 0)
                .orderByDesc(ExclusiveMerchant::getCreateTime)
                .last("limit 1"));
        if (match == null || match.getUserId() == null) {
            return null;
        }
        return new AttributionService.ExclusiveOwner(match.getUserId(), match.getDeptId());
    }

    /**
     * 写入或更新独家商家记录（upsert），以 merchantId + effectiveMonth 为去重键。
     */
    private void upsertExclusive(MerchantUserFeeRow row, long totalFee, BigDecimal ratio, YearMonth applyMonth) {
        String effectiveMonth = applyMonth.format(MONTH_FORMATTER);
        ExclusiveMerchant existing = exclusiveMerchantMapper.selectOne(new LambdaQueryWrapper<ExclusiveMerchant>()
                .eq(ExclusiveMerchant::getMerchantId, row.merchantId())
                .eq(ExclusiveMerchant::getEffectiveMonth, effectiveMonth)
                .eq(ExclusiveMerchant::getDeleted, 0)
                .last("limit 1"));
        ExclusiveMerchant target = existing == null ? new ExclusiveMerchant() : existing;
        target.setMerchantId(row.merchantId());
        target.setMerchantName(row.merchantName());
        target.setShopId(row.shopId());
        target.setUserId(row.userId());
        target.setDeptId(row.deptId());
        target.setEffectiveMonth(effectiveMonth);
        target.setServiceFee(row.merchantFee());
        target.setBusinessTotalFee(totalFee);
        target.setServiceFeeRatio(ratio);
        target.setStartDate(applyMonth.atDay(1));
        target.setEndDate(applyMonth.atEndOfMonth());
        target.setStatus(1);
        target.setRemark("auto-evaluated");
        target.setDeleted(0);

        if (existing == null) {
            target.setId(UUID.randomUUID());
            exclusiveMerchantMapper.insert(target);
        } else {
            exclusiveMerchantMapper.updateById(target);
        }
    }

    /**
     * 查询指定月份内每位招商员的总服务费收入（按 user_id 分组聚合）。
     */
    private List<UserTotalFeeRow> loadUserTotalFee(YearMonth month) {
        String sql = """
                SELECT user_id, SUM(COALESCE(settle_colonel_commission, 0)) AS total_fee
                FROM colonelsettlement_order
                WHERE deleted = 0
                  AND settle_time >= ?
                  AND settle_time < ?
                  AND user_id IS NOT NULL
                GROUP BY user_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new UserTotalFeeRow(
                parseUuid(rs.getString("user_id")),
                rs.getLong("total_fee")
        ), month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    /**
     * 查询指定月份内每个商家-招商员组合的服务费收入（按 merchantId + shopId + userId 分组聚合）。
     */
    private List<MerchantUserFeeRow> loadMerchantUserFee(YearMonth month) {
        String sql = """
                SELECT COALESCE(extra_data->>'merchant_id', CAST(shop_id AS TEXT)) AS merchant_id,
                       shop_id,
                       shop_name,
                       user_id,
                       dept_id,
                       SUM(COALESCE(settle_colonel_commission, 0)) AS merchant_fee
                FROM colonelsettlement_order
                WHERE deleted = 0
                  AND settle_time >= ?
                  AND settle_time < ?
                  AND user_id IS NOT NULL
                  AND COALESCE(extra_data->>'merchant_id', CAST(shop_id AS TEXT)) IS NOT NULL
                GROUP BY COALESCE(extra_data->>'merchant_id', CAST(shop_id AS TEXT)), shop_id, shop_name, user_id, dept_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MerchantUserFeeRow(
                rs.getString("merchant_id"),
                asLongObject(rs.getObject("shop_id")),
                rs.getString("shop_name"),
                parseUuid(rs.getString("user_id")),
                parseUuid(rs.getString("dept_id")),
                rs.getLong("merchant_fee")
        ), month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    /** 默认服务费比例阈值：70% */
    private static final BigDecimal DEFAULT_RATIO_THRESHOLD = new BigDecimal("70");

    /** 从配置域门面加载商家独家服务费比例阈值（DDD-CONFIG-003）。 */
    private BigDecimal loadRatioThreshold() {
        try {
            BigDecimal threshold = configDomainFacade.getExclusiveRules().merchantServiceFeeRatio();
            return threshold == null ? DEFAULT_RATIO_THRESHOLD : threshold;
        } catch (Exception ex) {
            return DEFAULT_RATIO_THRESHOLD;
        }
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
     * null 安全的 Object 转 Long，支持 Number 类型和字符串解析。
     */
    private Long asLongObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    /** 招商员总服务费行记录 */
    private record UserTotalFeeRow(UUID userId, long totalFee) {
    }

    /** 商家-招商员服务费行记录 */
    private record MerchantUserFeeRow(
            String merchantId,
            Long shopId,
            String merchantName,
            UUID userId,
            UUID deptId,
            long merchantFee
    ) {
    }
}
