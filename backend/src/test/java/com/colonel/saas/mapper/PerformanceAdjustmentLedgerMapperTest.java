package com.colonel.saas.mapper;

import com.colonel.saas.entity.PerformanceAdjustmentLedger;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.testsupport.DockerAvailable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DockerAvailable
class PerformanceAdjustmentLedgerMapperTest extends BaseIntegrationTest {

    @Autowired
    private PerformanceAdjustmentLedgerMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldInsertInputSnapshotAsPostgresJsonb() {
        PerformanceAdjustmentLedger ledger = newLedger();

        int rows = mapper.insert(ledger);

        assertThat(rows).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pg_typeof(input_snapshot)::text FROM performance_adjustment_ledger WHERE id = ?",
                String.class,
                ledger.getId())).isEqualTo("jsonb");
        assertThat(mapper.selectById(ledger.getId()).getInputSnapshot())
                .containsEntry("source", "refund")
                .containsEntry("amount", 1200);
    }

    private PerformanceAdjustmentLedger newLedger() {
        PerformanceAdjustmentLedger ledger = new PerformanceAdjustmentLedger();
        ledger.setId(UUID.randomUUID());
        ledger.setEventKey("mapper-jsonb-" + ledger.getId());
        ledger.setOrderId("ORDER-MAPPER-JSONB");
        ledger.setRefundId("REFUND-MAPPER-JSONB");
        ledger.setAdjustmentType("REFUND");
        ledger.setRefundAmount(1200L);
        ledger.setDeltaPayAmount(-1200L);
        ledger.setDeltaSettleAmount(-1000L);
        ledger.setDeltaEstimateServiceFee(0L);
        ledger.setDeltaEffectiveServiceFee(0L);
        ledger.setDeltaEstimateTechServiceFee(0L);
        ledger.setDeltaEffectiveTechServiceFee(0L);
        ledger.setDeltaEstimateServiceFeeExpense(0L);
        ledger.setDeltaEffectiveServiceFeeExpense(0L);
        ledger.setDeltaTalentCommission(0L);
        ledger.setDeltaEstimateServiceProfit(0L);
        ledger.setDeltaEffectiveServiceProfit(0L);
        ledger.setDeltaEstimateRecruiterCommission(0L);
        ledger.setDeltaEffectiveRecruiterCommission(0L);
        ledger.setDeltaEstimateChannelCommission(0L);
        ledger.setDeltaEffectiveChannelCommission(0L);
        ledger.setDeltaEstimateGrossProfit(0L);
        ledger.setDeltaEffectiveGrossProfit(0L);
        ledger.setOccurredAt(LocalDateTime.now());
        ledger.setInputSnapshot(Map.of("source", "refund", "amount", 1200));
        ledger.setCreatedAt(LocalDateTime.now());
        return ledger;
    }
}
