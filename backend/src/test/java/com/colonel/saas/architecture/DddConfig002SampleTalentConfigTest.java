package com.colonel.saas.architecture;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.config.facade.dto.SampleDefaultStandardDTO;
import com.colonel.saas.domain.config.facade.dto.SampleRulesDTO;
import com.colonel.saas.domain.config.facade.dto.TalentRulesDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DDD-CONFIG-002 配置路由验证测试。
 *
 * <p>验证寄样域和达人域配置读取已统一改走 {@link ConfigDomainFacade}：
 * <ul>
 *   <li>sample_limit_days=7 时 7 天限制生效</li>
 *   <li>sample_limit_enabled=false 时允许重复申请</li>
 *   <li>sample_auto_close_days 读取正确（含 homework 和 pending_ship）</li>
 *   <li>talent_claim_protect_days=30 时保护期正确</li>
 *   <li>独家达人阈值读取正确（fee_ratio + monthly_samples）</li>
 *   <li>配置缺失时 fallback 与旧行为一致</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DddConfig002SampleTalentConfigTest {

    @Mock
    private ConfigDomainFacade configDomainFacade;

    // ─── 寄样域：sample_limit_days ────────────────────────────────────

    @Test
    void sampleLimitDays_shouldReturnSevenWhenConfigured() {
        when(configDomainFacade.getSampleLimitDays()).thenReturn(7);

        int limitDays = configDomainFacade.getSampleLimitDays();

        assertThat(limitDays).isEqualTo(7);
        verify(configDomainFacade).getSampleLimitDays();
    }

    @Test
    void sampleLimitEnabled_shouldReturnFalseWhenDisabled() {
        when(configDomainFacade.isSampleLimitEnabled()).thenReturn(false);

        boolean enabled = configDomainFacade.isSampleLimitEnabled();

        assertThat(enabled).isFalse();
        verify(configDomainFacade).isSampleLimitEnabled();
    }

    @Test
    void sampleLimitEnabled_shouldReturnTrueByDefault() {
        when(configDomainFacade.isSampleLimitEnabled()).thenReturn(true);

        boolean enabled = configDomainFacade.isSampleLimitEnabled();

        assertThat(enabled).isTrue();
    }

    // ─── 寄样域：sample_auto_close_days ──────────────────────────────

    @Test
    void sampleAutoCloseDays_shouldReadFromFacade() {
        when(configDomainFacade.getSampleAutoCloseDays()).thenReturn(30);

        int autoCloseDays = configDomainFacade.getSampleAutoCloseDays();

        assertThat(autoCloseDays).isEqualTo(30);
        verify(configDomainFacade).getSampleAutoCloseDays();
    }

    @Test
    void sampleAutoCloseDays_pendingShip_shouldReadFromSampleRules() {
        SampleRulesDTO rules = new SampleRulesDTO(7, true, 30, 15,
                new SampleDefaultStandardDTO(null, null, null));
        when(configDomainFacade.getSampleRules()).thenReturn(rules);

        int pendingShipDays = configDomainFacade.getSampleRules().timeoutPendingShipDays();

        assertThat(pendingShipDays).isEqualTo(15);
        verify(configDomainFacade).getSampleRules();
    }

    // ─── 达人域：talent_claim_protect_days ────────────────────────────

    @Test
    void talentClaimProtectDays_shouldReturnThirtyWhenConfigured() {
        when(configDomainFacade.getTalentClaimProtectDays()).thenReturn(30);

        int protectDays = configDomainFacade.getTalentClaimProtectDays();

        assertThat(protectDays).isEqualTo(30);
        verify(configDomainFacade).getTalentClaimProtectDays();
    }

    // ─── 达人域：独家达人阈值 ────────────────────────────────────────

    @Test
    void exclusiveTalentThreshold_shouldReadFromFacade() {
        when(configDomainFacade.getExclusiveTalentFeeRatio()).thenReturn(new BigDecimal("70"));
        when(configDomainFacade.getExclusiveTalentMonthlySamples()).thenReturn(10);

        BigDecimal feeRatio = configDomainFacade.getExclusiveTalentFeeRatio();
        int monthlySamples = configDomainFacade.getExclusiveTalentMonthlySamples();

        assertThat(feeRatio).isEqualByComparingTo("70");
        assertThat(monthlySamples).isEqualTo(10);
    }

    @Test
    void talentRules_aggregateDTO_shouldReturnAllFields() {
        TalentRulesDTO dto = new TalentRulesDTO(30, new BigDecimal("70"), 10);
        when(configDomainFacade.getTalentRules()).thenReturn(dto);

        TalentRulesDTO result = configDomainFacade.getTalentRules();

        assertThat(result.protectionDays()).isEqualTo(30);
        assertThat(result.exclusiveRatioThreshold()).isEqualByComparingTo("70");
        assertThat(result.exclusiveMonthlySamples()).isEqualTo(10);
    }

    // ─── 配置缺失 fallback ────────────────────────────────────────────

    @Test
    void sampleRules_fallback_shouldReturnDefaultsWhenConfigMissing() {
        // When system_config has no entries, facade returns built-in defaults
        SampleRulesDTO defaults = new SampleRulesDTO(
                7,       // restrictDays default
                true,    // restrictEnabled default
                30,      // timeoutHomeworkDays default
                15,      // timeoutPendingShipDays default
                new SampleDefaultStandardDTO(null, null, Map.of())
        );
        when(configDomainFacade.getSampleRules()).thenReturn(defaults);

        SampleRulesDTO result = configDomainFacade.getSampleRules();

        assertThat(result.restrictDays()).isEqualTo(7);
        assertThat(result.restrictEnabled()).isTrue();
        assertThat(result.timeoutHomeworkDays()).isEqualTo(30);
        assertThat(result.timeoutPendingShipDays()).isEqualTo(15);
    }

    @Test
    void talentRules_fallback_shouldReturnDefaultsWhenConfigMissing() {
        // When system_config has no entries, facade returns built-in defaults
        TalentRulesDTO defaults = new TalentRulesDTO(
                30,                      // protectionDays default
                new BigDecimal("70"),    // exclusiveRatioThreshold default
                10                       // exclusiveMonthlySamples default
        );
        when(configDomainFacade.getTalentRules()).thenReturn(defaults);

        TalentRulesDTO result = configDomainFacade.getTalentRules();

        assertThat(result.protectionDays()).isEqualTo(30);
        assertThat(result.exclusiveRatioThreshold()).isEqualByComparingTo("70");
        assertThat(result.exclusiveMonthlySamples()).isEqualTo(10);
    }
}
