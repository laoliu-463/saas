package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class CommissionService {

    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.15");
    private static final String KEY_BIZ_RATIO = "commission.business_default_ratio";
    private static final String KEY_CHANNEL_RATIO = "commission.channel_default_ratio";

    private final JdbcTemplate jdbcTemplate;

    public CommissionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CommissionSummary calculate(List<ColonelsettlementOrder> orders) {
        long serviceFeeIncome = sum(orders, ColonelsettlementOrder::getSettleColonelCommission);
        long techServiceFee = sum(orders, ColonelsettlementOrder::getSettleColonelTechServiceFee);
        long talentCommission = sum(orders, ColonelsettlementOrder::getSettleSecondColonelCommission);

        long serviceFeeNet = Math.max(serviceFeeIncome - techServiceFee - talentCommission, 0L);
        BigDecimal bizRatio = loadRatio(KEY_BIZ_RATIO);
        BigDecimal channelRatio = loadRatio(KEY_CHANNEL_RATIO);

        long bizCommission = multiplyCent(serviceFeeNet, bizRatio);
        long channelCommission = multiplyCent(serviceFeeNet, channelRatio);
        long grossProfit = Math.max(serviceFeeNet - bizCommission - channelCommission, 0L);

        return new CommissionSummary(
                serviceFeeIncome,
                techServiceFee,
                talentCommission,
                serviceFeeNet,
                bizCommission,
                channelCommission,
                grossProfit,
                bizRatio,
                channelRatio
        );
    }

    private BigDecimal loadRatio(String key) {
        try {
            String sql = "SELECT config_value FROM system_config WHERE config_key = ? AND deleted = 0 LIMIT 1";
            String value = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString(1) : null, key);
            if (value == null || value.isBlank()) {
                return DEFAULT_RATIO;
            }
            BigDecimal ratio = new BigDecimal(value.trim());
            if (ratio.compareTo(BigDecimal.ZERO) < 0) {
                return DEFAULT_RATIO;
            }
            return ratio;
        } catch (Exception ignore) {
            return DEFAULT_RATIO;
        }
    }

    private long sum(List<ColonelsettlementOrder> rows, java.util.function.Function<ColonelsettlementOrder, Long> getter) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        long result = 0L;
        for (ColonelsettlementOrder row : rows) {
            Long value = getter.apply(row);
            result += value == null ? 0L : value;
        }
        return result;
    }

    private long multiplyCent(long amount, BigDecimal ratio) {
        if (amount <= 0L) {
            return 0L;
        }
        return BigDecimal.valueOf(amount)
                .multiply(ratio)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    public record CommissionSummary(
            long serviceFeeIncome,
            long techServiceFee,
            long talentCommission,
            long serviceFeeNet,
            long bizCommission,
            long channelCommission,
            long grossProfit,
            BigDecimal bizRatio,
            BigDecimal channelRatio
    ) {
    }
}
