package com.colonel.saas.service;

import com.colonel.saas.entity.CommissionRule;
import com.colonel.saas.mapper.CommissionRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionRuleServiceTest {

    @Mock
    private CommissionRuleMapper commissionRuleMapper;

    private CommissionRuleService service;

    @BeforeEach
    void setUp() {
        service = new CommissionRuleService(commissionRuleMapper);
    }

    @Test
    void resolveRatio_shouldPreferProductRuleOverActivityRule() {
        CommissionRule productRule = activeRule(
                CommissionRuleService.DIMENSION_PRODUCT,
                "P-1",
                CommissionRuleService.TYPE_RECRUITER,
                "0.25");
        when(commissionRuleMapper.selectOne(any())).thenReturn(productRule);

        BigDecimal ratio = service.resolveRatio(
                CommissionRuleService.TYPE_RECRUITER,
                new CommissionRuleService.CommissionResolutionContext("A-1", "P-1", UUID.randomUUID()),
                LocalDateTime.now());

        assertThat(ratio).isEqualByComparingTo("0.25");
    }

    @Test
    void resolveRatio_shouldFallbackFromProductToActivityAndStopAtFirstMatch() {
        CommissionRule activityRule = activeRule(
                CommissionRuleService.DIMENSION_ACTIVITY,
                "A-1",
                CommissionRuleService.TYPE_CHANNEL,
                "0.18");
        when(commissionRuleMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(activityRule);

        BigDecimal ratio = service.resolveRatio(
                CommissionRuleService.TYPE_CHANNEL,
                new CommissionRuleService.CommissionResolutionContext("A-1", "P-1", UUID.randomUUID()),
                LocalDateTime.now());

        assertThat(ratio).isEqualByComparingTo("0.18");
        verify(commissionRuleMapper, times(2)).selectOne(any());
    }

    @Test
    void create_shouldPersistValidatedRule() {
        CommissionRule rule = new CommissionRule();
        rule.setDimensionType(CommissionRuleService.DIMENSION_ACTIVITY);
        rule.setDimensionId("3559407");
        rule.setCommissionType(CommissionRuleService.TYPE_CHANNEL);
        rule.setRatio(new BigDecimal("0.18"));

        when(commissionRuleMapper.insert(any(CommissionRule.class))).thenReturn(1);

        CommissionRule created = service.create(rule);

        assertThat(created.getId()).isNotNull();
        ArgumentCaptor<CommissionRule> captor = ArgumentCaptor.forClass(CommissionRule.class);
        verify(commissionRuleMapper).insert(captor.capture());
        assertThat(captor.getValue().getDimensionType()).isEqualTo(CommissionRuleService.DIMENSION_ACTIVITY);
        assertThat(captor.getValue().getCommissionType()).isEqualTo(CommissionRuleService.TYPE_CHANNEL);
    }

    @Test
    void create_shouldNormalizeGlobalRuleWithoutDimensionId() {
        CommissionRule rule = new CommissionRule();
        rule.setDimensionType(" GLOBAL ");
        rule.setDimensionId("SHOULD_DROP");
        rule.setCommissionType(" RECRUITER ");
        rule.setRatio(new BigDecimal("0.16"));

        when(commissionRuleMapper.insert(any(CommissionRule.class))).thenReturn(1);

        CommissionRule created = service.create(rule);

        assertThat(created.getDimensionType()).isEqualTo(CommissionRuleService.DIMENSION_GLOBAL);
        assertThat(created.getDimensionId()).isNull();
        assertThat(created.getCommissionType()).isEqualTo(CommissionRuleService.TYPE_RECRUITER);
    }

    private CommissionRule activeRule(String dimensionType, String dimensionId, String commissionType, String ratio) {
        CommissionRule rule = new CommissionRule();
        rule.setDimensionType(dimensionType);
        rule.setDimensionId(dimensionId);
        rule.setCommissionType(commissionType);
        rule.setRatio(new BigDecimal(ratio));
        rule.setStatus(1);
        rule.setDeleted(0);
        return rule;
    }
}
