package com.colonel.saas.domain.config.facade;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.service.ShortTtlCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * DDD-CONFIG-002：ConfigDomainFacade 寄样/达人配置读取契约测试。
 */
@ExtendWith(MockitoExtension.class)
class LegacyConfigDomainFacadeTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    private ConfigDomainFacade facade;

    @BeforeEach
    void setUp() {
        BusinessRuleConfigService businessRuleConfigService =
                new BusinessRuleConfigService(systemConfigMapper, new ObjectMapper(), new ShortTtlCacheService());
        facade = new LegacyConfigDomainFacade(businessRuleConfigService);
    }

    @Test
    void sampleLimitDays_shouldBeSevenWhenConfigured() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS))
                .thenReturn(Optional.of(config("7")));

        assertThat(facade.getSampleLimitDays()).isEqualTo(7);
    }

    @Test
    void sampleLimitEnabled_falseShouldAllowRepeatApplyPath() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED))
                .thenReturn(Optional.of(config("false")));

        assertThat(facade.isSampleLimitEnabled()).isFalse();
    }

    @Test
    void sampleAutoCloseDays_shouldReadHomeworkTimeout() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS))
                .thenReturn(Optional.of(config("25")));

        assertThat(facade.getSampleAutoCloseDays()).isEqualTo(25);
    }

    @Test
    void talentClaimProtectDays_shouldBeThirtyWhenConfigured() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_PROTECTION_DAYS))
                .thenReturn(Optional.of(config("30")));

        assertThat(facade.getTalentClaimProtectDays()).isEqualTo(30);
    }

    @Test
    void exclusiveTalentThresholds_shouldReadRatioAndMonthlySamples() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO))
                .thenReturn(Optional.of(config("82")));
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES))
                .thenReturn(Optional.of(config("12")));

        assertThat(facade.getExclusiveTalentFeeRatio()).isEqualByComparingTo("82");
        assertThat(facade.getExclusiveTalentMonthlySamples()).isEqualTo(12);
    }

    @Test
    void missingConfig_shouldFallbackToLegacyDefaults() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS))
                .thenReturn(Optional.empty());
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED))
                .thenReturn(Optional.empty());
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS))
                .thenReturn(Optional.empty());
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_PROTECTION_DAYS))
                .thenReturn(Optional.empty());
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO))
                .thenReturn(Optional.empty());
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES))
                .thenReturn(Optional.empty());

        assertThat(facade.getSampleLimitDays()).isEqualTo(7);
        assertThat(facade.isSampleLimitEnabled()).isTrue();
        assertThat(facade.getSampleAutoCloseDays()).isEqualTo(30);
        assertThat(facade.getTalentClaimProtectDays()).isEqualTo(30);
        assertThat(facade.getExclusiveTalentFeeRatio()).isEqualByComparingTo("70");
        assertThat(facade.getExclusiveTalentMonthlySamples()).isEqualTo(10);
    }

    private static SystemConfig config(String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigValue(value);
        config.setDeleted(0);
        return config;
    }
}
