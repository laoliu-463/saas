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
import java.util.List;

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
        service = new BusinessRuleConfigService(systemConfigMapper, new ObjectMapper(), new ShortTtlCacheService());
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
    void shouldReadDecimalBooleanAliasesAndDeletedRows() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO)).thenReturn(Optional.of(config(" 82.5 ")));
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES)).thenReturn(Optional.of(config("12")));
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED)).thenReturn(Optional.of(config("1")));
        SystemConfig deleted = config("2");
        deleted.setDeleted(1);
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS)).thenReturn(Optional.of(deleted));

        assertThat(service.getTalentExclusiveRatioThreshold()).isEqualByComparingTo("82.5");
        assertThat(service.getTalentExclusiveMonthlySamples()).isEqualTo(12);
        assertThat(service.isSampleRestrictEnabled()).isTrue();
        assertThat(service.getSampleRestrictDays()).isEqualTo(7);
    }

    @Test
    void shouldFallbackForInvalidDecimalBooleanAndJson() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO)).thenReturn(Optional.of(config("bad")));
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED)).thenReturn(Optional.of(config("maybe")));
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_DEFAULT_STANDARD)).thenReturn(Optional.of(config("{bad-json")));

        assertThat(service.getTalentExclusiveRatioThreshold()).isEqualByComparingTo("70");
        assertThat(service.isSampleRestrictEnabled()).isTrue();
        assertThat(service.getSampleDefaultStandard().raw()).isEmpty();
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
    void shouldParsePromotionPickExtraRuleFromConfig() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.PROMOTION_PICK_EXTRA_RULE))
                .thenReturn(Optional.of(config("{\"format\":\"channel_{channel_code}_{product_id}\",\"encode\":\"none\"}")));

        var rule = service.getPromotionPickExtraRule();

        assertThat(rule.format()).isEqualTo("channel_{channel_code}_{product_id}");
        assertThat(rule.encode()).isEqualTo("none");
    }

    @Test
    void shouldFallbackPromotionPickExtraRuleWhenMissingOrInvalid() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.PROMOTION_PICK_EXTRA_RULE))
                .thenReturn(Optional.empty());

        var rule = service.getPromotionPickExtraRule();

        assertThat(rule.format()).isEqualTo("channel_{channel_code}");
        assertThat(rule.encode()).isEqualTo("none");
    }

    @Test
    void shouldParsePresetTalentTagsFromConfig() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS))
                .thenReturn(Optional.of(config("[\" 美妆 \",\"高转化\",\"美妆\",\"\"]")));

        List<String> tags = service.getPresetTalentTags();

        assertThat(tags).containsExactly("美妆", "高转化");
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

    @Test
    void invalidate_shouldIgnoreBlankAndSupportAllLocalEviction() {
        service.invalidate(" ");
        service.invalidateAll();

        assertThat(service.getTalentProtectionDays()).isEqualTo(30);
    }

    private SystemConfig config(String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigValue(value);
        config.setDeleted(0);
        return config;
    }
}
