package com.colonel.saas.domain.performance.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.performance.domain.ExclusiveMerchantRepository;
import com.colonel.saas.domain.performance.policy.ExclusiveMerchantPolicy;
import com.colonel.saas.entity.ExclusiveMerchant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 业绩域独家商家评估应用服务（DDD-PERF-005）。
 */
@Service
public class ExclusiveMerchantApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ExclusiveMerchantApplicationService.class);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal DEFAULT_RATIO_THRESHOLD = new BigDecimal("70");

    private final ConfigDomainFacade configDomainFacade;
    private final JdbcTemplate jdbcTemplate;
    private final ExclusiveMerchantRepository repository;

    public ExclusiveMerchantApplicationService(
            ConfigDomainFacade configDomainFacade,
            JdbcTemplate jdbcTemplate,
            ExclusiveMerchantRepository repository) {
        this.configDomainFacade = configDomainFacade;
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
    }

    @Transactional(rollbackFor = Exception.class)
    public int evaluatePreviousMonthAndApplyCurrentMonth() {
        return evaluateMonth(YearMonth.now().minusMonths(1), YearMonth.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public int evaluateMonth(YearMonth statsMonth, YearMonth applyMonth) {
        BigDecimal threshold = loadRatioThreshold();

        List<UserTotalFeeRow> userTotalFeeRows = loadUserTotalFee(statsMonth);
        Map<UUID, Long> totalByUser = userTotalFeeRows.stream()
                .collect(Collectors.toMap(UserTotalFeeRow::userId, UserTotalFeeRow::totalFee));

        List<MerchantUserFeeRow> merchantRows = loadMerchantUserFee(statsMonth);
        int upserted = 0;
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
            if (ExclusiveMerchantPolicy.meets(ratio, threshold)) {
                upsertExclusive(row, totalFee, ratio, applyMonth);
                upserted++;
            }
        }
        log.info("ExclusiveMerchantApplicationService evaluate done, statsMonth={}, applyMonth={}, upserted={}",
                statsMonth, applyMonth, upserted);
        return upserted;
    }

    private void upsertExclusive(MerchantUserFeeRow row, long totalFee, BigDecimal ratio, YearMonth applyMonth) {
        String effectiveMonth = applyMonth.format(MONTH_FORMATTER);
        ExclusiveMerchant existing = repository.findByMerchantIdAndMonth(row.merchantId(), effectiveMonth)
                .orElse(null);
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
            repository.save(target);
        } else {
            repository.update(target);
        }
    }

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

    private BigDecimal loadRatioThreshold() {
        try {
            BigDecimal threshold = configDomainFacade.getExclusiveRules().merchantServiceFeeRatio();
            return threshold == null ? DEFAULT_RATIO_THRESHOLD : threshold;
        } catch (Exception ex) {
            return DEFAULT_RATIO_THRESHOLD;
        }
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

    private record UserTotalFeeRow(UUID userId, long totalFee) {}

    private record MerchantUserFeeRow(
            String merchantId,
            Long shopId,
            String merchantName,
            UUID userId,
            UUID deptId,
            long merchantFee
    ) {}
}
