package com.colonel.saas.service;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessRuleConfigServiceTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    private BusinessRuleConfigService service;

    @BeforeEach
    void setUp() {
        service = new BusinessRuleConfigService(systemConfigMapper, new ObjectMapper());
    }

    @Test
    void shouldUseConfiguredValuesWhenPresent() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS)).thenReturn(Optional.of(config("3")));
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED)).thenReturn(Optional.of(config("false")));
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS)).thenReturn(Optional.of(config("20")));

        assertThat(service.getSampleRestrictDays()).isEqualTo(3);
        assertThat(service.isSampleRestrictEnabled()).isFalse();
        assertThat(service.getSampleTimeoutHomeworkDays()).isEqualTo(20);
    }

    @Test
    void shouldFallbackWhenConfigMissingOrInvalid() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_PROTECTION_DAYS)).thenReturn(Optional.of(config("abc")));
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_TIMEOUT_PENDING_SHIP_DAYS)).thenReturn(Optional.empty());

        assertThat(service.getTalentProtectionDays()).isEqualTo(30);
        assertThat(service.getSampleTimeoutPendingShipDays()).isEqualTo(15);
    }

    @Test
    void shouldParseSampleDefaultStandard() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_DEFAULT_STANDARD))
                .thenReturn(Optional.of(config("{\"min_30day_sales\":30000,\"min_level\":\"LV1\"}")));

        var standard = service.getSampleDefaultStandard();

        assertThat(standard.min30DaySales()).isEqualTo(30000L);
        assertThat(standard.minLevel()).isEqualTo("LV1");
    }

    @Test
    void shouldNormalizeSampleDefaultStandardLevel() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_DEFAULT_STANDARD))
                .thenReturn(Optional.of(config("{\"min_30day_sales\":30000,\"min_level\":\" lv2 \"}")));

        var standard = service.getSampleDefaultStandard();

        assertThat(standard.minLevel()).isEqualTo("LV2");
    }

    @Test
    void shouldCacheRawConfigLookupUntilInvalidated() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS))
                .thenReturn(Optional.of(config("7")))
                .thenReturn(Optional.of(config("9")));

        assertThat(service.getSampleRestrictDays()).isEqualTo(7);
        assertThat(service.getSampleRestrictDays()).isEqualTo(7);
        verify(systemConfigMapper, times(1)).findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS);

        service.invalidate(SystemConfigKeys.SAMPLE_RESTRICT_DAYS);

        assertThat(service.getSampleRestrictDays()).isEqualTo(9);
        verify(systemConfigMapper, times(2)).findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS);
    }

    private SystemConfig config(String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigValue(value);
        config.setDeleted(0);
        return config;
    }
}
