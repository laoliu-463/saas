package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.performance.domain.ExclusiveMerchantRepository;
import com.colonel.saas.domain.performance.policy.ExclusiveMerchantPolicy;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.dto.user.UserOptionResponse;
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
import java.util.Objects;
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
    private final UserDomainFacade userDomainFacade;

    public ExclusiveMerchantApplicationService(
            ConfigDomainFacade configDomainFacade,
            JdbcTemplate jdbcTemplate,
            ExclusiveMerchantRepository repository,
            UserDomainFacade userDomainFacade) {
        this.configDomainFacade = configDomainFacade;
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
        this.userDomainFacade = userDomainFacade;
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
        Map<UUID, UUID> deptByUser = loadRecruiterDeptMap(merchantRows);
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
                upsertExclusive(row, deptByUser.get(row.userId()), totalFee, ratio, applyMonth);
                upserted++;
            }
        }
        log.info("ExclusiveMerchantApplicationService evaluate done, statsMonth={}, applyMonth={}, upserted={}",
                statsMonth, applyMonth, upserted);
        return upserted;
    }

    private void upsertExclusive(
            MerchantUserFeeRow row,
            UUID recruiterDeptId,
            long totalFee,
            BigDecimal ratio,
            YearMonth applyMonth) {
        String effectiveMonth = applyMonth.format(MONTH_FORMATTER);
        ExclusiveMerchant existing = repository.findByMerchantIdAndMonth(row.merchantId(), effectiveMonth)
                .orElse(null);
        ExclusiveMerchant target = existing == null ? new ExclusiveMerchant() : existing;
        target.setMerchantId(row.merchantId());
        target.setMerchantName(row.merchantName());
        target.setShopId(row.shopId());
        target.setUserId(row.userId());
        target.setDeptId(recruiterDeptId);
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
                SELECT default_recruiter_user_id AS user_id,
                       SUM(COALESCE(effective_service_fee, 0)) AS total_fee
                FROM performance_records
                WHERE COALESCE(is_valid, true) = true
                  AND COALESCE(is_reversed, false) = false
                  AND settle_time >= ?
                  AND settle_time < ?
                  AND default_recruiter_user_id IS NOT NULL
                GROUP BY default_recruiter_user_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new UserTotalFeeRow(
                parseUuid(rs.getString("user_id")),
                rs.getLong("total_fee")
        ), month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private List<MerchantUserFeeRow> loadMerchantUserFee(YearMonth month) {
        String sql = """
                SELECT CAST(partner_id AS TEXT) AS merchant_id,
                       partner_id AS shop_id,
                       CAST(partner_id AS TEXT) AS shop_name,
                       default_recruiter_user_id AS user_id,
                       SUM(COALESCE(effective_service_fee, 0)) AS merchant_fee
                FROM performance_records
                WHERE COALESCE(is_valid, true) = true
                  AND COALESCE(is_reversed, false) = false
                  AND settle_time >= ?
                  AND settle_time < ?
                  AND default_recruiter_user_id IS NOT NULL
                  AND partner_id IS NOT NULL
                GROUP BY partner_id, default_recruiter_user_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MerchantUserFeeRow(
                rs.getString("merchant_id"),
                asLongObject(rs.getObject("shop_id")),
                rs.getString("shop_name"),
                parseUuid(rs.getString("user_id")),
                rs.getLong("merchant_fee")
        ), month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private Map<UUID, UUID> loadRecruiterDeptMap(List<MerchantUserFeeRow> rows) {
        List<UUID> userIds = rows.stream()
                .map(MerchantUserFeeRow::userId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserOptionResponse> users = userDomainFacade.getUsersByIds(userIds);
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        return users.stream()
                .filter(Objects::nonNull)
                .filter(user -> user.id() != null && user.deptId() != null)
                .collect(Collectors.toMap(
                        UserOptionResponse::id,
                        UserOptionResponse::deptId,
                        (left, right) -> left
                ));
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
            long merchantFee
    ) {}
}
