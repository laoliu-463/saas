package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CommissionService commissionService;

    @BeforeEach
    void setUp() {
        commissionService = new CommissionService(jdbcTemplate);
    }

    @Test
    void calculate_shouldUseConfigRatios() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), any()))
                .thenReturn("0.10")
                .thenReturn("0.20");

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setSettleColonelCommission(10000L);
        order.setSettleColonelTechServiceFee(1000L);
        order.setSettleSecondColonelCommission(2000L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(order));

        assertThat(summary.serviceFeeIncome()).isEqualTo(10000L);
        assertThat(summary.techServiceFee()).isEqualTo(1000L);
        assertThat(summary.talentCommission()).isEqualTo(2000L);
        assertThat(summary.serviceFeeNet()).isEqualTo(7000L);
        assertThat(summary.bizCommission()).isEqualTo(700L);
        assertThat(summary.channelCommission()).isEqualTo(1400L);
        assertThat(summary.grossProfit()).isEqualTo(4900L);
    }

    @Test
    void calculate_shouldFallbackWhenConfigMissing() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<String>>any(), any()))
                .thenReturn(null)
                .thenReturn(null);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setSettleColonelCommission(10000L);
        order.setSettleColonelTechServiceFee(0L);
        order.setSettleSecondColonelCommission(0L);

        CommissionService.CommissionSummary summary = commissionService.calculate(List.of(order));
        assertThat(summary.bizCommission()).isEqualTo(1500L);
        assertThat(summary.channelCommission()).isEqualTo(1500L);
    }
}
